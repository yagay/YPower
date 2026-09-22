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
        String attribution = buildAttributionExplanation(f, role);

        if (containsAny(all, "permission", "securityexception")) {
            add(f,
                    role + "：权限 / AppOps 访问链",
                    "应用会检查权限，是因为后续相机、定位、通知、后台能力或其他受保护 API 最终仍由 Android 系统按 UID、Permission 和 AppOps 校验。"
                            + "如果应用侧看到“已授权”但系统服务实际拒绝，就可能出现 SecurityException、功能失败或异常退出。",
                    "App Manager 的说明把 runtime permissions、AppOps、网络策略和电池优化分开管理，说明这些是不同层级的真实系统状态；"
                            + "因此“把 checkSelfPermission 改成 GRANTED”不能替代真实授权或 AppOps 状态。",
                    attribution,
                    "先确认 Manifest 是否声明权限，再检查系统权限、AppOps 和特殊权限状态。"
                            + "普通 dangerous permission 应优先真实授权；AppOps 或特殊权限按系统实际状态调整。"
                            + "如果仍然失败，再根据 SecurityException 的具体 API 和调用栈定位，不要继续扩大权限模拟范围。",
                    "App Manager / Android Permission & AppOps");
            return;
        }

        if (containsAny(all, "anr")) {
            add(f,
                    role + "：ANR / 主线程阻塞",
                    "应用检测到 ANR 或系统记录 ANR，是因为主线程长时间不能处理输入、Binder 回调或生命周期任务。"
                            + "常见来源包括锁竞争、磁盘 IO、数据库、耗时方法或同步等待。",
                    "xCrash 能捕获 ANR 并保存类似 tombstone 的信息；Matrix Trace Canary 专门记录 ANR、UI Block、慢方法和调用栈，"
                            + "IO Canary 关注文件 IO 问题。",
                    attribution,
                    "优先查看 ANR trace 的主线程栈，再检查 Binder 等待、锁、磁盘 IO、SQLite 和慢方法。"
                            + "如果同一栈反复出现，就修复该阻塞点，而不是修改环境检测。",
                    "xCrash / Tencent Matrix Trace Canary & IO Canary");
            return;
        }

        if (containsAny(all, "low memory", "低内存", "oom", "outofmemory")) {
            add(f,
                    role + "：内存 / 资源压力",
                    "系统或应用之所以记录低内存、OOM、线程或 FD 问题，是因为进程资源持续增长可能导致分配失败、LMKD 回收或 native 崩溃。",
                    "xCrash 的 README 说明它可以在崩溃报告中附带进程、线程、内存、FD 和网络统计；"
                            + "Matrix Resource Canary / Memory Hook 用于定位泄漏、Bitmap 重复和 native 内存问题。",
                    attribution,
                    "复现时同时比较 Java heap、native heap、线程数、FD 数和 Bitmap/大对象变化。"
                            + "优先修复持续增长项；如果只是系统内存压力导致退出，应减少峰值或后台资源占用。",
                    "xCrash / Tencent Matrix Resource Canary & Memory Hook");
            return;
        }

        if (containsAny(all, "native crash", "sigsegv", "signal", "native")) {
            add(f,
                    role + "：Native 崩溃",
                    "Native 崩溃通常来自非法内存访问、ABI/指令问题、JNI 错误、SO 冲突或 Hook 对 native 代码产生影响。"
                            + "它不能仅凭“设备有 Root/Hook”就直接判定原因。",
                    "xCrash 专门捕获 native crash 并生成 tombstone；ByteHook 强调 PLT Hook 的稳定性、兼容性和避免递归；"
                            + "ShadowHook 文档明确列出 inline Hook 与 signal handler、ELF island 等稳定性问题，并建议在疑难崩溃时通过禁用 Hook 做对照。",
                    attribution,
                    "先保留 tombstone、signal、fault address、崩溃 SO、Build ID 和 native backtrace。"
                            + "若只有某个 Hook Provider 开启时复现，做 Provider A/B；若关闭所有 Hook 仍复现，则按普通 native 崩溃继续查 SO/ABI/JNI。",
                    "xCrash / ByteHook / ShadowHook");
            return;
        }

        if (containsAny(all, "unsatisfiedlinkerror", "dlopen", "abi", "so")) {
            add(f,
                    role + "：SO / ABI 加载失败",
                    "应用会在启动或功能初始化时加载 native library；ABI 不匹配、依赖 SO 缺失、加载路径错误或 page-size 不兼容都会导致 dlopen/UnsatisfiedLinkError。",
                    "xCrash 的 native crash/tombstone 能保留加载失败和 native 现场；其项目 Issues 也持续跟踪新 Android 版本和 16 KB page-size 兼容问题。",
                    attribution,
                    "以实际报错的 SO 为入口，核对 APK 内 ABI、设备 ABI、依赖库、加载路径和 16 KB page-size 兼容性。"
                            + "不要先改 Root/Hook 环境，除非 A/B 已证明加载失败只在 Hook 开启时发生。",
                    "xCrash linker/native diagnostics");
            return;
        }

        if (containsAny(all, "sqlite")) {
            add(f,
                    role + "：SQLite 调用链",
                    "应用检测或出现 SQLite 异常，通常是 SQL 语句、锁竞争、主线程数据库访问或数据库状态问题，而不是环境完整性检测。",
                    "Matrix SQLite Lint 的说明是按 SQLite 官方最佳实践自动评估 SQL 质量，并在开发/测试阶段发现 SQLite 性能隐患。",
                    attribution,
                    "查看实际 SQLiteException、SQL、数据库锁和线程；检查是否在主线程做数据库操作。"
                            + "如果是慢查询或语句质量问题，可按 Matrix SQLite Lint 的方向优化具体 SQL。",
                    "Tencent Matrix SQLite Lint");
            return;
        }

        if (containsAny(all, "ssl", "tls", "handshake")) {
            add(f,
                    role + "：TLS / 网络握手",
                    "应用检查 TLS 或出现握手失败，是为了确认与服务端建立可信连接；错误时间、证书链、TLS 版本、代理/VPN 或服务端配置都可能导致失败。",
                    "这类问题属于网络/证书链诊断，不应通过关闭证书校验解决；YPower 只根据实际失败日志做归因。",
                    attribution,
                    "核对设备时间、服务端证书链、TLS 版本和代理/VPN 环境，再对照实际 SSLHandshakeException 或网络错误。"
                            + "如果服务端拒绝，应查看响应阶段而不是改本地证书校验。",
                    "Android TLS diagnostics");
            return;
        }

        if (containsAny(all, "root", "su", "magisk", "kernelsu", "apatch")) {
            add(f,
                    role + "：Root 环境检测",
                    "一些应用会检查 su、Root 管理应用、危险属性、test-keys、可写 system 或 systemless 痕迹，"
                            + "目的是判断设备是否存在可改变应用/系统行为的高权限环境。",
                    "RootBeer README 明确把这些检查描述为“Root 的迹象”而不是 100% 证明，并列出 Root 管理应用、su、test-keys、ro.debuggable/ro.secure、RW system 等检查；"
                            + "它还专门提醒某些检查会有误报，例如部分厂商 ROM 自带 BusyBox。",
                    attribution,
                    "先重复复现，确认每次都是 Root 相关检查后紧接异常退出。"
                            + "如果应用要求受支持的完整设备环境，应在官方支持的设备状态下复测；"
                            + "如果是你自己的应用，则调整自己的兼容策略或不要把单一 Root 信号作为崩溃条件。"
                            + "不要仅凭“检测到 Root”就直接认定它是根因。",
                    "RootBeer / RootRoot");
            return;
        }

        if (containsAny(all, "lsposed", "xposed", "hook", "/proc/self/maps", "注入")) {
            add(f,
                    role + "：Hook / 注入环境检测",
                    "应用读取 /proc/self/maps、查询 Hook 类或检查注入库，通常是为了确认运行时是否有第三方代码进入自身进程。"
                            + "这种检查也可能用来解释兼容性问题，因为 Hook 会改变方法实现、内存布局或 native 调用链。",
                    "ShadowHook 的手册把稳定性、兼容性作为核心目标，并明确说明 Hook 与 native crash capture signal handler、ELF island 等机制可能相互影响；"
                            + "还给出“禁用 ShadowHook 来调查疑难崩溃是否与 inline Hook 有关”的灰度排查思路。"
                            + "ByteHook 同样强调多 Hook、不冲突、自动处理新加载 SO 和递归保护。",
                    attribution,
                    "先关闭会改变行为的 Identity/Permission Provider，只保留最小诊断 Hook；如果恢复正常，再逐个恢复 Provider 做二分。"
                            + "若关闭所有 YPower Hook 后仍复现，则不要继续归因于 YPower Hook，转向普通 Java/Native/环境问题。",
                    "LSPosed / ByteHook / ShadowHook");
            return;
        }

        if (containsAny(all, "bootloader", "avb", "verifiedboot", "完整性")) {
            add(f,
                    role + "：Bootloader / AVB 完整性检查",
                    "应用读取 verified boot、vbmeta、flash lock 等属性，是为了了解设备启动链是否处于已验证、受支持的完整性状态。",
                    "Root/完整性类项目通常把 boot/AVB 属性作为环境信号之一；这类本地属性只能说明应用实际读取了什么，"
                            + "不能等同于 Play Integrity、Key Attestation 或服务端最终判定。",
                    attribution,
                    "先确认同一属性查询是否稳定紧邻退出。"
                            + "若应用依赖 Play Integrity/Attestation，应在应用官方支持的设备完整性状态下复测；"
                            + "如果是你自己的应用，则检查本地属性策略是否过度严格或与服务端判定重复。",
                    "Android Verified Boot / Attestation model");
            return;
        }

        if (containsAny(all, "mount", "systemless", "/data/adb")) {
            add(f,
                    role + "：Mount / Systemless 环境检查",
                    "应用读取 mountinfo、/proc/mounts 或 /data/adb，通常是为了识别 overlay、bind mount、systemless root 或模块挂载。"
                            + "现代 Root 不一定修改 /system 本体，所以 mount namespace 会成为常见环境信号。",
                    "RootBeer README 明确指出传统“/system 是否可写”对 systemless root 并不充分；这也是为什么新检测项目会补充 mount/systemless 线索。",
                    attribution,
                    "先减少目标 App 的 Hook 面并重启，再重复相同操作。"
                            + "如果相同 mount 检查每次都紧邻退出，再把它作为主要环境兼容原因；"
                            + "如果是你自己的应用，应避免单一 mount 信号直接导致崩溃。",
                    "RootBeer / proc-mount detection projects");
            return;
        }

        if (containsAny(all, "package", "安装环境查询")) {
            add(f,
                    role + "：包名 / 安装环境查询",
                    "应用枚举安装包或查询特定包名，常见目的是识别 Root 管理器、Hook 管理器、调试工具或安装来源。",
                    "RootBeer 明确使用 PackageManager 查询已知 Root 管理应用；Ruru 等检测项目也把可疑包和环境组件作为检测维度。"
                            + "这些结果仍只是环境信号，预置包名列表也可能过时或误报。",
                    attribution,
                    "查看详细证据里的实际包名/安装来源，并重复复现。"
                            + "如果每次都是同一查询后紧接退出，说明它更可能参与判断；"
                            + "如果是你自己的应用，建议把单个包名从“直接退出条件”降级为多因素风险信号。",
                    "RootBeer / Ruru package-detection ideas");
            return;
        }

        if (containsAny(all, "property", "系统属性", "debuggable", "secure", "build")) {
            add(f,
                    role + "：系统属性检测",
                    "应用读取 ro.debuggable、ro.secure、ro.build.tags、ro.boot.* 等属性，通常是为了判断设备是否为调试构建、测试签名或非标准启动环境。",
                    "RootBeer README 把 ro.debuggable/ro.secure 归为危险属性，把 test-keys 作为 Root/非标准环境的迹象；"
                            + "同时它强调单个结果不能作为 100% 的 Root 证明。",
                    attribution,
                    "以详细证据中的具体 property key 为准，确认它是否在多次复现中稳定紧邻退出。"
                            + "如果是你自己的应用，优先调整对单一属性的强制退出逻辑，改为记录或多因素判断；"
                            + "如果是第三方应用，则在其官方支持环境下验证兼容性。",
                    "RootBeer / RootRoot / Ruru");
            return;
        }

        if (containsAny(all, "主动退出", "system.exit", "killprocess", "runtime.halt")) {
            add(f,
                    role + "：应用主动退出调用",
                    "System.exit、Runtime.halt 或 killProcess 只是“退出动作”，通常不是最前面的原因；"
                            + "真正的触发条件往往发生在它之前的环境检查、权限失败或业务判断。",
                    "xCrash 的思路是保存崩溃/ANR 现场；YPower 在此基础上用运行时事件时间线把退出前的检测调用与终点关联起来。",
                    attribution,
                    "向前看 100 ms、500 ms、1.5 s 内最高关联的非退出检测项，并重复同一操作验证。"
                            + "如果没有稳定前置检测，就不要把某个环境项强行定为原因。",
                    "YPower runtime correlation / xCrash timeline approach");
            return;
        }

        if (containsAny(all, "java", "runtime crash", "crash")) {
            add(f,
                    role + "：Java / Runtime Crash",
                    "Java 崩溃是未处理异常沿调用栈传播到主线程或业务线程的结果，最重要的是异常类型、message 和第一个业务栈帧。",
                    "xCrash 的 README 明确支持捕获 Java crash、native crash 和 ANR，并生成类似 Android tombstone 的报告；"
                            + "它还可附带线程、内存、FD、网络等进程信息辅助定位。",
                    attribution,
                    "先修复首个业务栈异常。SecurityException 查真实权限/AppOps，IllegalStateException 查生命周期/状态机，"
                            + "NullPointerException 查空值来源。修复后用同一诊断流程确认崩溃是否消失。",
                    "xCrash");
        }
    }

    private static String buildAttributionExplanation(DiagnosticFinding f, String role) {
        StringBuilder b = new StringBuilder();
        b.append("本项被标记为").append(role)
                .append("，关联分数为 ").append(f.correlationScore).append("/100。");
        if (!f.evidence.isEmpty()) {
            b.append(" YPower 在同一次运行会话中记录到了对应检测证据，并将它与真实退出时间进行关联。");
        }
        if (f.attributionRank == 2) {
            b.append(" 它不是最高分原因，但分数达到次要归因阈值且与主要归因差距较小。");
        }
        return b.toString();
    }

    private static void add(
            DiagnosticFinding f,
            String title,
            String whyDetected,
            String projectExplanation,
            String whyAttributed,
            String repair,
            String source
    ) {
        f.recommendations.add(new FixRecommendation(
                title,
                whyDetected,
                projectExplanation,
                whyAttributed,
                repair,
                source
        ));
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
