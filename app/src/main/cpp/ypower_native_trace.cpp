#include <jni.h>
#include <android/log.h>
#include <bytehook.h>

#include <atomic>
#include <cerrno>
#include <cinttypes>
#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <mutex>
#include <pthread.h>
#include <signal.h>
#include <string>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <unwind.h>
#include <vector>

#define LOG_TAG "YPowerTrace"

static std::atomic<bool> g_enabled(false);
static std::mutex g_lock;
static std::string g_package;
static std::string g_session;
static std::vector<bytehook_stub_t> g_stubs;
static bool g_hooks_installed = false;

static thread_local bool g_emitting = false;

static int64_t now_ms() {
    struct timespec ts{};
    clock_gettime(CLOCK_REALTIME, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000LL + ts.tv_nsec / 1000000LL;
}

static int64_t now_ns_monotonic() {
    struct timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}

static std::string json_escape(const std::string &in) {
    std::string out;
    out.reserve(in.size() + 16);
    for (char c : in) {
        switch (c) {
            case '\\': out += "\\\\"; break;
            case '"': out += "\\\""; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) >= 0x20) out += c;
                break;
        }
    }
    return out;
}

static std::string current_thread_name() {
    char name[64] = {0};
    if (pthread_getname_np(pthread_self(), name, sizeof(name)) != 0) {
        return "";
    }
    return name;
}

struct UnwindState {
    uintptr_t frames[24];
    size_t count;
};

static _Unwind_Reason_Code unwind_callback(
        struct _Unwind_Context *context,
        void *arg) {
    auto *state = reinterpret_cast<UnwindState *>(arg);
    if (state->count >= 24) return _URC_END_OF_STACK;

    uintptr_t pc = static_cast<uintptr_t>(_Unwind_GetIP(context));
    if (pc != 0) state->frames[state->count++] = pc;
    return _URC_NO_REASON;
}

static std::string native_backtrace() {
    UnwindState state{};
    _Unwind_Backtrace(unwind_callback, &state);

    std::string out;
    int added = 0;

    for (size_t i = 0; i < state.count && added < 12; ++i) {
        void *addr = reinterpret_cast<void *>(state.frames[i]);
        Dl_info info{};
        if (dladdr(addr, &info) == 0 || info.dli_fname == nullptr) continue;

        const char *base = strrchr(info.dli_fname, '/');
        base = base == nullptr ? info.dli_fname : base + 1;

        if (strstr(base, "libypower_native_trace.so") != nullptr
                || strstr(base, "libbytehook.so") != nullptr) {
            continue;
        }

        uintptr_t offset = info.dli_fbase == nullptr
                ? 0
                : reinterpret_cast<uintptr_t>(addr)
                  - reinterpret_cast<uintptr_t>(info.dli_fbase);

        char frame[256];
        snprintf(frame, sizeof(frame), "%s+0x%" PRIxPTR, base, offset);

        if (!out.empty()) out += " <- ";
        out += frame;
        added++;
    }

    return out;
}

static std::string caller_source() {
    void *addr = BYTEHOOK_RETURN_ADDRESS();
    if (addr == nullptr) return "";

    Dl_info info{};
    if (dladdr(addr, &info) == 0 || info.dli_fname == nullptr) return "";

    uintptr_t offset = 0;
    if (info.dli_fbase != nullptr) {
        offset = reinterpret_cast<uintptr_t>(addr)
                - reinterpret_cast<uintptr_t>(info.dli_fbase);
    }

    const char *base = strrchr(info.dli_fname, '/');
    base = base == nullptr ? info.dli_fname : base + 1;

    char buf[256];
    snprintf(buf, sizeof(buf), "%s+0x%" PRIxPTR, base, offset);
    return buf;
}

static std::string errno_text(int saved_errno) {
    if (saved_errno == 0) return "";
    char buf[256];
    snprintf(buf, sizeof(buf), "errno=%d(%s)", saved_errno, strerror(saved_errno));
    return buf;
}

static const char *rule_for_path(const char *path) {
    if (path == nullptr) return "UNKNOWN";
    std::string s(path);

    // Most-specific paths first. Do not let generic /data/adb swallow Magisk/KSU/APatch.
    if (s.find("magisk") != std::string::npos) return "ROOT_FILE_MAGISK";
    if (s.find("kernelsu") != std::string::npos || s.find("/data/adb/ksu") != std::string::npos) {
        return "ROOT_FILE_KERNELSU";
    }
    if (s.find("apatch") != std::string::npos || s.find("/data/adb/ap") != std::string::npos) {
        return "ROOT_FILE_APATCH";
    }
    size_t len = s.size();
    if ((len >= 3 && s.compare(len - 3, 3, "/su") == 0)
            || s.find("/system/bin/su") != std::string::npos
            || s.find("/system/xbin/su") != std::string::npos
            || s == "/sbin/su") {
        return "ROOT_FILE_SU";
    }
    if (s.find("/proc/self/maps") != std::string::npos) return "HOOK_PROC_MAPS";
    if (s.find("/proc/self/status") != std::string::npos) return "DEBUG_PROC_STATUS";
    if (s.find("mountinfo") != std::string::npos || s.find("/proc/mount") != std::string::npos) {
        return "MOUNT_PROC_MOUNT";
    }
    if (s.find("/data/adb") != std::string::npos) return "ROOT_DATA_ADB";
    return "UNKNOWN";
}

static bool sensitive_path(const char *path) {
    if (path == nullptr) return false;
    const char *rule = rule_for_path(path);
    return strcmp(rule, "UNKNOWN") != 0;
}

static void emit_event(
        const char *type,
        const char *rule_id,
        const std::string &input,
        const std::string &result,
        bool matched,
        const std::string &exception,
        const std::string &source,
        int64_t duration_ns) {
    if (!g_enabled.load(std::memory_order_relaxed) || g_emitting) return;

    g_emitting = true;

    std::string package_name;
    std::string session_id;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        package_name = g_package;
        session_id = g_session;
    }

    pid_t pid = getpid();
    pid_t tid = static_cast<pid_t>(syscall(SYS_gettid));

    std::string stack = native_backtrace();

    std::string json = "{";
    json += "\"ts\":" + std::to_string(now_ms());
    json += ",\"package\":\"" + json_escape(package_name) + "\"";
    json += ",\"sessionId\":\"" + json_escape(session_id) + "\"";
    json += ",\"type\":\"" + json_escape(type == nullptr ? "" : type) + "\"";
    json += ",\"ruleId\":\"" + json_escape(rule_id == nullptr ? "UNKNOWN" : rule_id) + "\"";
    json += ",\"input\":\"" + json_escape(input) + "\"";
    json += ",\"value\":\"" + json_escape(input) + "\"";
    json += ",\"result\":\"" + json_escape(result) + "\"";
    json += ",\"matched\":";
    json += matched ? "true" : "false";
    json += ",\"exception\":\"" + json_escape(exception) + "\"";
    json += ",\"source\":\"" + json_escape(source) + "\"";
    json += ",\"pid\":" + std::to_string(pid);
    json += ",\"tid\":" + std::to_string(tid);
    json += ",\"thread\":\"" + json_escape(current_thread_name()) + "\"";
    json += ",\"process\":\"" + json_escape(package_name) + "\"";
    json += ",\"durationNs\":" + std::to_string(duration_ns);
    json += ",\"stack\":\"" + json_escape(stack) + "\"";
    json += "}";

    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, json.c_str());
    g_emitting = false;
}

static bool caller_allow_filter(const char *caller_path_name, void *) {
    if (caller_path_name == nullptr) return false;

    std::string caller(caller_path_name);
    if (caller.find("/system/") != std::string::npos
            || caller.find("/apex/") != std::string::npos
            || caller.find("/vendor/") != std::string::npos
            || caller.find("/product/") != std::string::npos
            || caller.find("libbytehook.so") != std::string::npos
            || caller.find("libypower_native_trace.so") != std::string::npos
            || caller.find("liblog.so") != std::string::npos
            || caller.find("libbase.so") != std::string::npos
            || caller.find("libc.so") != std::string::npos) {
        return false;
    }
    return true;
}

static bool interesting_symbol(const char *symbol) {
    if (symbol == nullptr) return false;
    std::string s(symbol);
    if (s.rfind("Java_", 0) == 0) return true;
    if (s.find("JNI_OnLoad") != std::string::npos) return true;
    if (s.find("RegisterNatives") != std::string::npos) return true;

    std::string lower = s;
    for (char &ch : lower) ch = static_cast<char>(tolower(ch));

    return lower.find("root") != std::string::npos
            || lower.find("debug") != std::string::npos
            || lower.find("security") != std::string::npos
            || lower.find("integrity") != std::string::npos
            || lower.find("attest") != std::string::npos
            || lower.find("check") != std::string::npos;
}

static void dlopen_pre_callback(const char *filename, void *) {
    if (filename == nullptr || !g_enabled.load(std::memory_order_relaxed)) return;
    emit_event(
            "native_linker",
            "LINKER_DLOPEN",
            std::string("dlopen ") + filename,
            "begin",
            false,
            "",
            caller_source(),
            0
    );
}

static void dlopen_post_callback(const char *filename, int result, void *) {
    if (filename == nullptr || !g_enabled.load(std::memory_order_relaxed)) return;
    emit_event(
            "native_linker",
            "LINKER_DLOPEN",
            std::string("dlopen ") + filename,
            result == 0 ? "loaded" : "failed",
            false,
            result == 0 ? "" : "dlopen failed",
            caller_source(),
            0
    );
}

static void *proxy_dlsym(void *handle, const char *symbol) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns_monotonic();
    std::string source = caller_source();

    void *ret = BYTEHOOK_CALL_PREV(proxy_dlsym, handle, symbol);
    int saved_errno = errno;

    if (interesting_symbol(symbol)) {
        std::string result;
        if (ret == nullptr) {
            result = "null";
        } else {
            char buf[64];
            snprintf(buf, sizeof(buf), "%p", ret);
            result = buf;
        }

        emit_event(
                "native_linker",
                "LINKER_DLSYM",
                std::string("dlsym ") + (symbol == nullptr ? "" : symbol),
                result,
                false,
                ret == nullptr ? errno_text(saved_errno) : "",
                source,
                now_ns_monotonic() - start
        );
    }

    errno = saved_errno;
    return ret;
}

static int proxy_access(const char *pathname, int mode) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns_monotonic();
    std::string source = caller_source();

    int ret = BYTEHOOK_CALL_PREV(proxy_access, pathname, mode);
    int saved_errno = errno;

    if (sensitive_path(pathname)) {
        emit_event(
                "native_file",
                rule_for_path(pathname),
                std::string("access ") + (pathname == nullptr ? "" : pathname),
                std::to_string(ret),
                ret == 0,
                ret == 0 ? "" : errno_text(saved_errno),
                source,
                now_ns_monotonic() - start
        );
    }

    errno = saved_errno;
    return ret;
}

static FILE *proxy_fopen(const char *pathname, const char *mode) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns_monotonic();
    std::string source = caller_source();

    FILE *ret = BYTEHOOK_CALL_PREV(proxy_fopen, pathname, mode);
    int saved_errno = errno;

    if (sensitive_path(pathname)) {
        emit_event(
                "native_file",
                rule_for_path(pathname),
                std::string("fopen ") + (pathname == nullptr ? "" : pathname),
                ret == nullptr ? "null" : "opened",
                ret != nullptr,
                ret != nullptr ? "" : errno_text(saved_errno),
                source,
                now_ns_monotonic() - start
        );
    }

    errno = saved_errno;
    return ret;
}

static int proxy_stat(const char *pathname, struct stat *buf) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns_monotonic();
    std::string source = caller_source();

    int ret = BYTEHOOK_CALL_PREV(proxy_stat, pathname, buf);
    int saved_errno = errno;

    if (sensitive_path(pathname)) {
        emit_event(
                "native_file",
                rule_for_path(pathname),
                std::string("stat ") + (pathname == nullptr ? "" : pathname),
                std::to_string(ret),
                ret == 0,
                ret == 0 ? "" : errno_text(saved_errno),
                source,
                now_ns_monotonic() - start
        );
    }

    errno = saved_errno;
    return ret;
}

static int proxy_lstat(const char *pathname, struct stat *buf) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns_monotonic();
    std::string source = caller_source();

    int ret = BYTEHOOK_CALL_PREV(proxy_lstat, pathname, buf);
    int saved_errno = errno;

    if (sensitive_path(pathname)) {
        emit_event(
                "native_file",
                rule_for_path(pathname),
                std::string("lstat ") + (pathname == nullptr ? "" : pathname),
                std::to_string(ret),
                ret == 0,
                ret == 0 ? "" : errno_text(saved_errno),
                source,
                now_ns_monotonic() - start
        );
    }

    errno = saved_errno;
    return ret;
}

static ssize_t proxy_readlink(const char *pathname, char *buf, size_t bufsiz) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns_monotonic();
    std::string source = caller_source();

    ssize_t ret = BYTEHOOK_CALL_PREV(proxy_readlink, pathname, buf, bufsiz);
    int saved_errno = errno;

    if (sensitive_path(pathname)) {
        emit_event(
                "native_file",
                rule_for_path(pathname),
                std::string("readlink ") + (pathname == nullptr ? "" : pathname),
                std::to_string(ret),
                ret >= 0,
                ret >= 0 ? "" : errno_text(saved_errno),
                source,
                now_ns_monotonic() - start
        );
    }

    errno = saved_errno;
    return ret;
}

static long proxy_ptrace(int request, pid_t pid, void *addr, void *data) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns_monotonic();
    std::string source = caller_source();

    long ret = BYTEHOOK_CALL_PREV(proxy_ptrace, request, pid, addr, data);
    int saved_errno = errno;

    bool matched = ret == -1 && saved_errno == EPERM;
    emit_event(
            "native_debugger",
            "NATIVE_PTRACE",
            "request=" + std::to_string(request) + " pid=" + std::to_string(pid),
            std::to_string(ret),
            matched,
            ret == -1 ? errno_text(saved_errno) : "",
            source,
            now_ns_monotonic() - start
    );

    errno = saved_errno;
    return ret;
}

static void proxy_abort(void) {
    BYTEHOOK_STACK_SCOPE();
    std::string source = caller_source();
    emit_event(
            "native_exit",
            "EXIT_NATIVE_ABORT",
            "abort()",
            "",
            true,
            "",
            source,
            0
    );
    BYTEHOOK_CALL_PREV(proxy_abort);
}

static void proxy_exit(int status) {
    BYTEHOOK_STACK_SCOPE();
    std::string source = caller_source();
    emit_event(
            "native_exit",
            "EXIT_NATIVE_EXIT",
            "exit(" + std::to_string(status) + ")",
            "",
            true,
            "",
            source,
            0
    );
    BYTEHOOK_CALL_PREV(proxy_exit, status);
}

static void proxy__exit(int status) {
    BYTEHOOK_STACK_SCOPE();
    std::string source = caller_source();
    emit_event(
            "native_exit",
            "EXIT_NATIVE_EXIT",
            "_exit(" + std::to_string(status) + ")",
            "",
            true,
            "",
            source,
            0
    );
    BYTEHOOK_CALL_PREV(proxy__exit, status);
}

static int proxy_kill(pid_t pid, int sig) {
    BYTEHOOK_STACK_SCOPE();
    std::string source = caller_source();

    if (pid == getpid()) {
        emit_event(
                "native_exit",
                "EXIT_NATIVE_KILL",
                "kill(pid=" + std::to_string(pid) + ", sig=" + std::to_string(sig) + ")",
                "",
                true,
                "",
                source,
                0
        );
    }

    return BYTEHOOK_CALL_PREV(proxy_kill, pid, sig);
}

static int proxy_tgkill(int tgid, int tid, int sig) {
    BYTEHOOK_STACK_SCOPE();
    std::string source = caller_source();

    if (tgid == getpid()) {
        emit_event(
                "native_exit",
                "EXIT_NATIVE_KILL",
                "tgkill(tgid=" + std::to_string(tgid)
                        + ", tid=" + std::to_string(tid)
                        + ", sig=" + std::to_string(sig) + ")",
                "",
                true,
                "",
                source,
                0
        );
    }

    return BYTEHOOK_CALL_PREV(proxy_tgkill, tgid, tid, sig);
}

static void add_hook_for_library(
        const char *callee,
        const char *symbol,
        void *proxy) {
    bytehook_stub_t stub = bytehook_hook_partial(
            caller_allow_filter,
            nullptr,
            callee,
            symbol,
            proxy,
            nullptr,
            nullptr
    );
    if (stub != nullptr) {
        g_stubs.push_back(stub);
    }
}

static void add_hook(const char *symbol, void *proxy) {
    add_hook_for_library("libc.so", symbol, proxy);
}

static void install_hooks_locked() {
    if (g_hooks_installed) return;

    add_hook("access", reinterpret_cast<void *>(proxy_access));
    add_hook("fopen", reinterpret_cast<void *>(proxy_fopen));
    add_hook("stat", reinterpret_cast<void *>(proxy_stat));
    add_hook("lstat", reinterpret_cast<void *>(proxy_lstat));
    add_hook("readlink", reinterpret_cast<void *>(proxy_readlink));
    add_hook("ptrace", reinterpret_cast<void *>(proxy_ptrace));
    add_hook("abort", reinterpret_cast<void *>(proxy_abort));
    add_hook("exit", reinterpret_cast<void *>(proxy_exit));
    add_hook("_exit", reinterpret_cast<void *>(proxy__exit));
    add_hook("kill", reinterpret_cast<void *>(proxy_kill));
    add_hook("tgkill", reinterpret_cast<void *>(proxy_tgkill));
    add_hook_for_library("libdl.so", "dlsym", reinterpret_cast<void *>(proxy_dlsym));
    bytehook_add_dlopen_callback(dlopen_pre_callback, dlopen_post_callback, nullptr);

    g_hooks_installed = true;
}

static void uninstall_hooks_locked() {
    bytehook_del_dlopen_callback(dlopen_pre_callback, dlopen_post_callback, nullptr);
    for (bytehook_stub_t stub : g_stubs) {
        if (stub != nullptr) bytehook_unhook(stub);
    }
    g_stubs.clear();
    g_hooks_installed = false;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_yagay_ypower_hook_NativeTraceBridge_nativeEnable(
        JNIEnv *env,
        jclass,
        jstring package_name,
        jstring session_id) {
    const char *pkg = package_name == nullptr
            ? ""
            : env->GetStringUTFChars(package_name, nullptr);
    const char *session = session_id == nullptr
            ? ""
            : env->GetStringUTFChars(session_id, nullptr);

    {
        std::lock_guard<std::mutex> guard(g_lock);
        g_package = pkg == nullptr ? "" : pkg;
        g_session = session == nullptr ? "" : session;
        install_hooks_locked();
        g_enabled.store(true, std::memory_order_release);
    }

    if (package_name != nullptr && pkg != nullptr) {
        env->ReleaseStringUTFChars(package_name, pkg);
    }
    if (session_id != nullptr && session != nullptr) {
        env->ReleaseStringUTFChars(session_id, session);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_yagay_ypower_hook_NativeTraceBridge_nativeDisable(
        JNIEnv *,
        jclass) {
    g_enabled.store(false, std::memory_order_release);

    std::lock_guard<std::mutex> guard(g_lock);
    uninstall_hooks_locked();
    g_package.clear();
    g_session.clear();
}
