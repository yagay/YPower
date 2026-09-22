package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.FixRecommendation;

import java.util.Locale;

public final class FixRecommendationEngine {
    private FixRecommendationEngine() {}

    public static void apply(DiagnosticReport report) {
        for (DiagnosticFinding finding : report.findings) {
            finding.recommendations.clear();
        }

        if (report.lastExitTimestamp <= 0) return;

        for (DiagnosticFinding finding : report.findings) {
            if (finding.attributionRank == 1 || finding.attributionRank == 2) {
                addForAttributedCause(finding);
            }
        }
    }

    private static void addForAttributedCause(DiagnosticFinding f) {
        String category = safe(f.category).toLowerCase(Locale.ROOT);
        String title = safe(f.title).toLowerCase(Locale.ROOT);
        String summary = safe(f.summary).toLowerCase(Locale.ROOT);
        String all = category + " " + title + " " + summary;
        String role = f.attributionRank == 1 ? "主要归因" : "次要归因";

        if (containsAny(all, "permission", "securityexception")) {
            add(f,
                    role + "建议：先验证真实权限/AppOps",
                    "当前归因指向权限状态/访问链。先确认目标 App 实际声明并获得了所需权限，"
                            + "再检查对应 AppOps 或特殊权限。不要只依赖把 checkSelfPermission Hook 成 GRANTED，"
                            + "因为系统服务仍可能按真实 UID 权限拒绝调用。",
                    "App Manager / Android Permission & AppOps");
            return;
        }

        if (containsAny(all, "anr")) {
            add(f,
                    role + "建议：定位主线程阻塞",
                    "当前归因指向 ANR。优先保留 ANR trace，检查主线程、Binder 等待、锁竞争、"
                            + "磁盘 IO 和耗时方法；如果问题稳定复现，再对照 Matrix Trace Canary / IO Canary 的方式缩小阻塞点。",
                    "xCrash ANR / Tencent Matrix");
            return;
        }

        if (containsAny(all, "low memory", "低内存", "oom", "outofmemory")) {
            add(f,
                    role + "建议：围绕内存压力继续取证",
                    "当前归因指向内存/资源压力。建议复现时同时采集 Java heap、native heap、线程、FD、Bitmap 等，"
                            + "优先排查持续增长项，而不是修改环境检测。",
                    "xCrash / Tencent Matrix Resource & Memory diagnostics");
            return;
        }

        if (containsAny(all, "native crash", "sigsegv", "signal", "native")) {
            add(f,
                    role + "建议：按 Native 崩溃链处理",
                    "当前归因更接近 Native 侧问题。保留 tombstone、signal、fault address、崩溃 SO、Build ID 和 native backtrace。"
                            + "若只有启用某个 Hook Provider 时才复现，再做 Provider A/B 对比确认是否为 Hook 冲突。",
                    "xCrash / ByteHook / ShadowHook");
            return;
        }

        if (containsAny(all, "unsatisfiedlinkerror", "dlopen", "abi", "so")) {
            add(f,
                    role + "建议：检查 SO/ABI 加载链",
                    "当前归因指向 native library 加载。核对设备 ABI、APK 内 SO、依赖库、加载路径和 16 KB page-size 兼容性，"
                            + "以实际 dlopen/UnsatisfiedLinkError 指向的库为准。",
                    "xCrash linker diagnostics");
            return;
        }

        if (containsAny(all, "sqlite")) {
            add(f,
                    role + "建议：从数据库调用链定位",
                    "当前归因指向 SQLite。优先检查异常 SQL、锁竞争、慢查询以及是否在主线程做数据库操作；"
                            + "可参考 Matrix SQLite Lint 的排查方式。",
                    "Tencent Matrix SQLite Lint");
            return;
        }

        if (containsAny(all, "ssl", "tls", "handshake")) {
            add(f,
                    role + "建议：检查 TLS 实际失败点",
                    "当前归因指向 TLS/握手。核对系统时间、证书链、TLS 版本、代理/VPN 干扰和服务端配置。"
                            + "不要通过关闭证书校验来规避问题。",
                    "Android TLS diagnostics");
            return;
        }

        if (containsAny(all, "root", "su", "magisk", "kernelsu", "apatch")) {
            add(f,
                    role + "建议：验证 Root 检测是否真的触发退出",
                    "当前归因显示 Root 相关检测与退出时间接近。建议先关闭目标 App 的非必要 YPower 兼容 Hook，"
                            + "在应用官方支持的设备状态下做一次 A/B 复测。只有重复出现“Root 检测 → 紧接退出”的链路时，"
                            + "才把它作为主要环境兼容原因。",
                    "RootBeer / RootRoot detection model");
            return;
        }

        if (containsAny(all, "lsposed", "xposed", "hook", "/proc/self/maps", "注入")) {
            add(f,
                    role + "建议：按 Hook Provider 做二分定位",
                    "当前归因显示 Hook/注入环境检测与退出高度相关。先关闭身份模拟、权限模拟等会改变行为的 Provider，"
                            + "只保留最小诊断 Hook；若恢复正常，再逐个恢复 Provider，确定具体冲突点。",
                    "LSPosed / ByteHook / ShadowHook");
            return;
        }

        if (containsAny(all, "bootloader", "avb", "verifiedboot", "完整性")) {
            add(f,
                    role + "建议：确认本地属性检查是否真正导致退出",
                    "当前归因指向 Bootloader/AVB 属性查询。先重复复现并确认查询后立即出现退出。"
                            + "若最终决策来自 Play Integrity、Key Attestation 或服务端，则应以应用支持的官方设备状态为准，"
                            + "不要把一次本地属性读取直接当成最终拒绝原因。",
                    "Android Verified Boot / Attestation model");
            return;
        }

        if (containsAny(all, "mount", "systemless", "/data/adb")) {
            add(f,
                    role + "建议：验证 Mount/Systemless 检测链",
                    "当前归因显示 mount/systemless 检测与退出接近。建议先减少目标 App 的 Hook 面并重启目标 App，"
                            + "再重复同一操作；只有相同 mount 检查反复紧邻退出时，才把它作为主要环境原因。",
                    "Root detection projects / proc-mount diagnostics");
            return;
        }

        if (containsAny(all, "package", "安装环境查询")) {
            add(f,
                    role + "建议：确认具体被查询的包名",
                    "当前归因指向包/安装环境查询。查看详细证据中的实际包名或安装来源，"
                            + "再做一次相同流程复现；如果每次都是同一查询后紧接退出，说明该包查询更值得继续定位。",
                    "RootBeer / Ruru package detection ideas");
            return;
        }

        if (containsAny(all, "property", "系统属性", "debuggable", "secure", "build")) {
            add(f,
                    role + "建议：围绕实际属性 key 做复现",
                    "当前归因指向系统属性检测。以详细证据中的具体 key 为准，重复一次同样操作并确认其与退出时序是否稳定。"
                            + "如果多个属性同时出现，优先看离退出最近且重复性最高的一个。",
                    "Ruru / RootRoot environment-property detection ideas");
            return;
        }

        if (containsAny(all, "主动退出", "system.exit", "killprocess", "runtime.halt")) {
            add(f,
                    role + "建议：继续向前追最近检测链",
                    "主动退出只是终点，不是根因。查看它前 100 ms、500 ms、1.5 s 内最高关联的检测项，"
                            + "并优先对该检测项做重复复现。",
                    "YPower runtime correlation / xCrash timeline approach");
            return;
        }

        if (containsAny(all, "java", "runtime crash", "crash")) {
            add(f,
                    role + "建议：从首个业务栈异常开始修",
                    "当前归因指向 Java/Runtime Crash。查看 FATAL EXCEPTION 的异常类型、message 和第一段业务栈；"
                            + "若是 SecurityException、IllegalStateException、NullPointerException，分别从真实权限、生命周期和空值路径排查。",
                    "xCrash Java crash workflow");
        }
    }

    private static void add(DiagnosticFinding f, String title, String detail, String source) {
        f.recommendations.add(new FixRecommendation(title, detail, source));
    }

    private static boolean containsAny(String value, String... keys) {
        for (String key : keys) {
            if (value.contains(key.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
