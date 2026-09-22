package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.FixRecommendation;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FixRecommendationEngine {
    private FixRecommendationEngine() {}

    public static void apply(DiagnosticReport report) {
        for (DiagnosticFinding finding : report.findings) {
            addForFinding(finding);
        }
        deduplicate(report);
    }

    private static void addForFinding(DiagnosticFinding f) {
        String category = safe(f.category).toLowerCase(Locale.ROOT);
        String title = safe(f.title).toLowerCase(Locale.ROOT);
        String summary = safe(f.summary).toLowerCase(Locale.ROOT);
        String all = category + " " + title + " " + summary;

        if (containsAny(all, "permission", "securityexception")) {
            add(f,
                    "优先使用真实权限/AppOps，而不是只模拟 GRANTED",
                    "检查目标 App 是否声明该权限；普通 dangerous permission 可由系统授权，AppOps/特殊权限应调整真实系统状态。"
                            + "如果调用最终仍由系统服务校验，单纯 Hook 权限查询不会获得真实能力。",
                    "App Manager / Android permission & AppOps model",
                    true);
        }

        if (containsAny(all, "low memory", "低内存", "oom", "outofmemory")) {
            add(f,
                    "检查内存峰值、线程和 FD",
                    "复现时保留进程内存、线程数、FD 和关键堆栈；优先排查大对象、Bitmap、native heap、线程泄漏和文件描述符泄漏。",
                    "xCrash / Matrix Resource & Memory diagnostics",
                    false);
        }

        if (containsAny(all, "anr")) {
            add(f,
                    "定位主线程阻塞点",
                    "重点查看 ANR 前后的主线程堆栈、Binder 等待、锁竞争、磁盘 IO 和耗时方法；避免在主线程做长时间 IO/数据库/网络操作。",
                    "xCrash ANR / Matrix Trace Canary & IO Canary",
                    false);
        }

        if (containsAny(all, "native crash", "sigsegv", "signal", "native")) {
            add(f,
                    "先按 Native 崩溃处理，不要直接归因于 Root/Hook",
                    "保留 tombstone、signal、fault address、崩溃 SO、Build ID 和 native backtrace。"
                            + "如果仅在 Hook 开启时复现，再做关闭 Hook 的 A/B 对比。",
                    "xCrash native tombstone / ByteHook / ShadowHook",
                    false);
        }

        if (containsAny(all, "unsatisfiedlinkerror", "dlopen", "abi", "so")) {
            add(f,
                    "检查 ABI、SO 完整性和加载路径",
                    "核对 APK 内 ABI 与设备 ABI、SO 是否缺失/损坏、依赖库是否齐全，以及 16 KB page-size 兼容性。"
                            + "优先依据实际 dlopen/UnsatisfiedLinkError 日志定位具体库。",
                    "xCrash linker diagnostics",
                    false);
        }

        if (containsAny(all, "sqlite")) {
            add(f,
                    "检查数据库语句和数据库线程使用",
                    "查看异常 SQL、锁竞争、慢查询和数据库是否在主线程执行；必要时做 SQLite lint/trace。",
                    "Matrix SQLite Lint",
                    false);
        }

        if (containsAny(all, "ssl", "tls", "handshake")) {
            add(f,
                    "检查网络时间、证书链和 TLS 配置",
                    "核对系统时间、服务端证书链、TLS 版本以及代理/VPN 对连接的影响。不要通过关闭证书校验来“修复”。",
                    "Android TLS diagnostics",
                    false);
        }

        if (containsAny(all, "background", "后台", "低内存结束进程", "资源使用异常结束")) {
            add(f,
                    "检查后台限制与电池策略",
                    "可以先确认 Doze、App Standby、后台 AppOps、后台数据策略是否限制目标 App，再观察问题是否复现。",
                    "App Manager root/ADB controls",
                    true);
        }

        if (containsAny(all, "root", "su", "magisk", "kernelsu", "apatch")) {
            add(f,
                    "用干净环境做 A/B 诊断",
                    "先关闭 YPower 对该 App 的非必要兼容 Hook，并在应用官方支持的设备环境中复测。"
                            + "如果只有修改环境时退出，可将其标记为环境兼容问题；不要仅凭检测到 Root 就认定它一定是崩溃原因。",
                    "RootBeer detection model",
                    true);
        }

        if (containsAny(all, "lsposed", "xposed", "hook", "/proc/self/maps", "注入")) {
            add(f,
                    "减少目标 App 的 Hook 面",
                    "先只保留诊断所需 Provider，关闭身份/权限模拟等会改变行为的 Hook，再重新运行。"
                            + "如果关闭 Hook 后恢复正常，可继续按 Provider 二分定位冲突。",
                    "LSPosed / ByteHook / ShadowHook stability model",
                    true);
        }

        if (containsAny(all, "bootloader", "avb", "verifiedboot", "完整性")) {
            add(f,
                    "区分本地环境检测与服务端完整性判定",
                    "如果只是读取 boot/AVB 属性，先记录它与退出的时间关系；如果结果来自 Play Integrity/Key Attestation/服务端，"
                            + "应使用应用支持的官方设备状态或联系应用方，不把本地属性读取直接等同于最终拒绝原因。",
                    "Android integrity / attestation model",
                    false);
        }

        if (containsAny(all, "mount", "systemless", "/data/adb")) {
            add(f,
                    "检查 Systemless/Mount 是否只是相关信号",
                    "先做关闭目标 App 非必要 Hook、重启目标 App 的 A/B；如果仍然退出，再结合退出前时间线判断 mount 检查是否真正相关。",
                    "Root detection projects / proc-mount diagnostics",
                    true);
        }

        if (containsAny(all, "主动退出", "system.exit", "killprocess", "runtime.halt")) {
            add(f,
                    "向前追踪最近的检测调用",
                    "主动退出本身通常不是根因。优先查看退出前 100 ms、500 ms、1.5 s 内的包查询、文件、属性和命令检测，"
                            + "并用相同操作重复复现确认。",
                    "YPower correlation model inspired by crash timelines",
                    false);
        }

        if (containsAny(all, "java", "runtime crash", "crash")) {
            add(f,
                    "优先修复最顶层 Java 异常",
                    "查看 FATAL EXCEPTION 的异常类型、message 和第一段业务栈；如果是 SecurityException/IllegalStateException/"
                            + "NullPointerException，应分别从真实权限/生命周期状态/空值路径处理。",
                    "xCrash Java crash workflow",
                    false);
        }
    }

    private static void add(DiagnosticFinding f, String title, String detail, String source, boolean ypowerCanHelp) {
        f.recommendations.add(new FixRecommendation(title, detail, source, ypowerCanHelp));
    }

    private static void deduplicate(DiagnosticReport report) {
        Set<String> seen = new HashSet<>();
        for (DiagnosticFinding f : report.findings) {
            f.recommendations.removeIf(rec -> !seen.add(f.id + "|" + rec.title + "|" + rec.detail));
        }
    }

    private static boolean containsAny(String value, String... keys) {
        for (String key : keys) if (value.contains(key.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
