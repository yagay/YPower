package com.yagay.ypower.hook;

import java.util.Locale;

public final class DetectionRuleIds {
    private DetectionRuleIds() {}

    public static final String UNKNOWN = "UNKNOWN";
    public static final String ROOT_FILE_SU = "ROOT_FILE_SU";
    public static final String ROOT_FILE_MAGISK = "ROOT_FILE_MAGISK";
    public static final String ROOT_FILE_KERNELSU = "ROOT_FILE_KERNELSU";
    public static final String ROOT_FILE_APATCH = "ROOT_FILE_APATCH";
    public static final String ROOT_DATA_ADB = "ROOT_DATA_ADB";
    public static final String HOOK_PROC_MAPS = "HOOK_PROC_MAPS";
    public static final String DEBUG_PROC_STATUS = "DEBUG_PROC_STATUS";
    public static final String MOUNT_PROC_MOUNT = "MOUNT_PROC_MOUNT";
    public static final String PACKAGE_MAGISK = "PACKAGE_MAGISK";
    public static final String PACKAGE_KERNELSU = "PACKAGE_KERNELSU";
    public static final String PACKAGE_APATCH = "PACKAGE_APATCH";
    public static final String PACKAGE_LSPOSED = "PACKAGE_LSPOSED";
    public static final String PACKAGE_XPOSED = "PACKAGE_XPOSED";
    public static final String PACKAGE_FRIDA = "PACKAGE_FRIDA";
    public static final String PACKAGE_SHIZUKU = "PACKAGE_SHIZUKU";
    public static final String PACKAGE_ENUMERATION = "PACKAGE_ENUMERATION";
    public static final String PROP_VERIFIED_BOOT = "PROP_VERIFIED_BOOT";
    public static final String PROP_VBMETA_STATE = "PROP_VBMETA_STATE";
    public static final String PROP_FLASH_LOCKED = "PROP_FLASH_LOCKED";
    public static final String PROP_DEBUGGABLE = "PROP_DEBUGGABLE";
    public static final String PROP_SECURE = "PROP_SECURE";
    public static final String PROP_BUILD_TAGS = "PROP_BUILD_TAGS";
    public static final String PROP_BUILD_TYPE = "PROP_BUILD_TYPE";
    public static final String PROP_GENERIC = "PROP_GENERIC";
    public static final String CMD_SU = "CMD_SU";
    public static final String CMD_GETPROP = "CMD_GETPROP";
    public static final String CMD_MOUNT = "CMD_MOUNT";
    public static final String CMD_SELINUX = "CMD_SELINUX";
    public static final String DEBUG_IS_CONNECTED = "DEBUG_IS_CONNECTED";
    public static final String DEBUG_WAITING = "DEBUG_WAITING";
    public static final String PERMISSION_QUERY = "PERMISSION_QUERY";
    public static final String EXIT_SYSTEM = "EXIT_SYSTEM";
    public static final String EXIT_HALT = "EXIT_HALT";
    public static final String EXIT_KILL_PROCESS = "EXIT_KILL_PROCESS";
    public static final String NATIVE_PTRACE = "NATIVE_PTRACE";
    public static final String EXIT_NATIVE_ABORT = "EXIT_NATIVE_ABORT";
    public static final String EXIT_NATIVE_EXIT = "EXIT_NATIVE_EXIT";
    public static final String EXIT_NATIVE_KILL = "EXIT_NATIVE_KILL";
    public static final String JAVA_LOAD_LIBRARY = "JAVA_LOAD_LIBRARY";
    public static final String LINKER_DLOPEN = "LINKER_DLOPEN";
    public static final String LINKER_DLSYM = "LINKER_DLSYM";

    public static final String JAVA_UNCAUGHT_EXCEPTION = "JAVA_UNCAUGHT_EXCEPTION";
    public static final String JAVA_DEFAULT_EXCEPTION_HANDLER_SET = "JAVA_DEFAULT_EXCEPTION_HANDLER_SET";
    public static final String JAVA_THREAD_EXCEPTION_HANDLER_SET = "JAVA_THREAD_EXCEPTION_HANDLER_SET";
    public static final String COROUTINE_UNHANDLED_EXCEPTION = "COROUTINE_UNHANDLED_EXCEPTION";
    public static final String RXJAVA2_GLOBAL_ERROR = "RXJAVA2_GLOBAL_ERROR";
    public static final String RXJAVA3_GLOBAL_ERROR = "RXJAVA3_GLOBAL_ERROR";
    public static final String RXJAVA2_ERROR_HANDLER_SET = "RXJAVA2_ERROR_HANDLER_SET";
    public static final String RXJAVA3_ERROR_HANDLER_SET = "RXJAVA3_ERROR_HANDLER_SET";

    public static final String KERNEL_UNAME_QUERY = "KERNEL_UNAME_QUERY";
    public static final String KERNEL_PROC_VERSION = "KERNEL_PROC_VERSION";
    public static final String KERNEL_CMDLINE_QUERY = "KERNEL_CMDLINE_QUERY";
    public static final String KERNEL_OSRELEASE_QUERY = "KERNEL_OSRELEASE_QUERY";
    public static final String KERNEL_SYS_VERSION_QUERY = "KERNEL_SYS_VERSION_QUERY";
    public static final String KERNEL_KPTR_QUERY = "KERNEL_KPTR_QUERY";

    public static final String SELINUX_ENFORCE_READ = "SELINUX_ENFORCE_READ";
    public static final String SELINUX_CONTEXT_READ = "SELINUX_CONTEXT_READ";
    public static final String SELINUX_POLICY_READ = "SELINUX_POLICY_READ";
    public static final String SELINUX_XATTR_QUERY = "SELINUX_XATTR_QUERY";

    public static final String MEMORY_SMAPS_QUERY = "MEMORY_SMAPS_QUERY";
    public static final String MEMORY_FD_QUERY = "MEMORY_FD_QUERY";
    public static final String MEMORY_TASK_QUERY = "MEMORY_TASK_QUERY";
    public static final String MEMORY_LINKER_ENUM_QUERY = "MEMORY_LINKER_ENUM_QUERY";
    public static final String MEMORY_SIGNAL_QUERY = "MEMORY_SIGNAL_QUERY";
    public static final String MEMORY_VDSO_QUERY = "MEMORY_VDSO_QUERY";
    public static final String MEMORY_MPROTECT_QUERY = "MEMORY_MPROTECT_QUERY";

    public static final String KEYSTORE_INSTANCE_QUERY = "KEYSTORE_INSTANCE_QUERY";
    public static final String KEY_ATTESTATION_CHALLENGE = "KEY_ATTESTATION_CHALLENGE";
    public static final String KEY_STRONGBOX_REQUEST = "KEY_STRONGBOX_REQUEST";
    public static final String KEY_CERT_CHAIN_QUERY = "KEY_CERT_CHAIN_QUERY";
    public static final String KEY_SECURITY_LEVEL_QUERY = "KEY_SECURITY_LEVEL_QUERY";
    public static final String PLAY_INTEGRITY_REQUEST = "PLAY_INTEGRITY_REQUEST";
    public static final String PLAY_INTEGRITY_STANDARD_PREPARE = "PLAY_INTEGRITY_STANDARD_PREPARE";
    public static final String PLAY_INTEGRITY_STANDARD_REQUEST = "PLAY_INTEGRITY_STANDARD_REQUEST";
    public static final String PLAY_INTEGRITY_TOKEN_QUERY = "PLAY_INTEGRITY_TOKEN_QUERY";

    public static final String SELINUX_ACCESS_PROBE = "SELINUX_ACCESS_PROBE";
    public static final String SELINUX_STATUS_SEQNO = "SELINUX_STATUS_SEQNO";
    public static final String SELINUX_POLICYLOAD_QUERY = "SELINUX_POLICYLOAD_QUERY";
    public static final String APP_ZYGOTE_PROBE = "APP_ZYGOTE_PROBE";

    public static final String PROCESS_FORK_QUERY = "PROCESS_FORK_QUERY";
    public static final String PROCESS_WAITPID_QUERY = "PROCESS_WAITPID_QUERY";
    public static final String PTRACE_ATTACH_QUERY = "PTRACE_ATTACH_QUERY";
    public static final String PTRACE_EVENTMSG_QUERY = "PTRACE_EVENTMSG_QUERY";
    public static final String PTRACE_SYSCALL_QUERY = "PTRACE_SYSCALL_QUERY";
    public static final String PTRACE_DETACH_QUERY = "PTRACE_DETACH_QUERY";

    public static final String APP_SIGNATURE_QUERY = "APP_SIGNATURE_QUERY";
    public static final String SELF_APK_READ = "SELF_APK_READ";
    public static final String SELF_DEX_READ = "SELF_DEX_READ";
    public static final String SELF_SO_READ = "SELF_SO_READ";
    public static final String CERTIFICATE_DIGEST_QUERY = "CERTIFICATE_DIGEST_QUERY";

    public static String forPath(String value) {
        String s = lower(value);

        // Most-specific rules must win before generic parent directories such as /data/adb.
        if (s.contains("magisk")) return ROOT_FILE_MAGISK;
        if (s.contains("kernelsu") || s.contains("/data/adb/ksu")) return ROOT_FILE_KERNELSU;
        if (s.contains("apatch") || s.contains("/data/adb/ap")) return ROOT_FILE_APATCH;
        if (s.endsWith("/su") || s.contains("/system/bin/su") || s.contains("/system/xbin/su")
                || s.equals("/sbin/su")) {
            return ROOT_FILE_SU;
        }
        if (s.contains("/proc/self/smaps")) return MEMORY_SMAPS_QUERY;
        if (s.contains("/proc/self/maps")) return HOOK_PROC_MAPS;
        if (s.contains("/proc/self/status")) return DEBUG_PROC_STATUS;
        if (s.contains("/proc/self/fd") || s.matches(".*/proc/[0-9]+/fd(/.*)?")) return MEMORY_FD_QUERY;
        if (s.contains("/proc/self/task") || s.matches(".*/proc/[0-9]+/task(/.*)?")) return MEMORY_TASK_QUERY;
        if (s.equals("/proc/version")) return KERNEL_PROC_VERSION;
        if (s.equals("/proc/cmdline")) return KERNEL_CMDLINE_QUERY;
        if (s.equals("/proc/sys/kernel/osrelease")) return KERNEL_OSRELEASE_QUERY;
        if (s.equals("/proc/sys/kernel/version")) return KERNEL_SYS_VERSION_QUERY;
        if (s.equals("/proc/sys/kernel/kptr_restrict")) return KERNEL_KPTR_QUERY;
        if (s.equals("/sys/fs/selinux/access")) return SELINUX_ACCESS_PROBE;
        if (s.equals("/sys/fs/selinux/status")) return SELINUX_STATUS_SEQNO;
        if (s.equals("/sys/fs/selinux/policyload")) return SELINUX_POLICYLOAD_QUERY;
        if (s.equals("/sys/fs/selinux/enforce")) return SELINUX_ENFORCE_READ;
        if (s.equals("/proc/self/attr/current")) return SELINUX_CONTEXT_READ;
        if (s.startsWith("/sys/fs/selinux/")) return SELINUX_POLICY_READ;
        if (s.contains("mountinfo") || s.contains("/proc/mount")) return MOUNT_PROC_MOUNT;
        if (s.contains("/data/adb")) return ROOT_DATA_ADB;
        return UNKNOWN;
    }

    public static String forPackage(String value) {
        String s = lower(value);
        if (s.contains("magisk")) return PACKAGE_MAGISK;
        if (s.contains("kernelsu")) return PACKAGE_KERNELSU;
        if (s.contains("apatch")) return PACKAGE_APATCH;
        if (s.contains("lsposed")) return PACKAGE_LSPOSED;
        if (s.contains("xposed")) return PACKAGE_XPOSED;
        if (s.contains("frida")) return PACKAGE_FRIDA;
        if (s.contains("shizuku")) return PACKAGE_SHIZUKU;
        return UNKNOWN;
    }

    public static String forProperty(String value) {
        String s = lower(value);
        if (s.contains("verifiedbootstate")) return PROP_VERIFIED_BOOT;
        if (s.contains("vbmeta.device_state")) return PROP_VBMETA_STATE;
        if (s.contains("flash.locked")) return PROP_FLASH_LOCKED;
        if (s.contains("ro.debuggable")) return PROP_DEBUGGABLE;
        if (s.contains("ro.secure")) return PROP_SECURE;
        if (s.contains("ro.build.tags")) return PROP_BUILD_TAGS;
        if (s.contains("ro.build.type")) return PROP_BUILD_TYPE;
        return PROP_GENERIC;
    }

    public static String forCommand(String value) {
        String s = lower(value);
        if (s.contains("which su") || s.matches(".*(^|\\s|/)su(\\s|$).*")) return CMD_SU;
        if (s.contains("getprop")) return CMD_GETPROP;
        if (s.contains("mount")) return CMD_MOUNT;
        if (s.contains("getenforce")) return CMD_SELINUX;
        return UNKNOWN;
    }

    public static boolean propertyValueLooksMatched(String ruleId, String result) {
        String s = lower(result).trim();
        switch (ruleId) {
            case PROP_VERIFIED_BOOT:
                return !s.isEmpty() && !"green".equals(s);
            case PROP_VBMETA_STATE:
                return "unlocked".equals(s) || "orange".equals(s);
            case PROP_FLASH_LOCKED:
                return "0".equals(s) || "false".equals(s);
            case PROP_DEBUGGABLE:
                return "1".equals(s) || "true".equals(s);
            case PROP_SECURE:
                return "0".equals(s) || "false".equals(s);
            case PROP_BUILD_TAGS:
                return s.contains("test-keys");
            default:
                return false;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
