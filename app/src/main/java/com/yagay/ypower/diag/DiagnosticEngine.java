package com.yagay.ypower.diag;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;

import com.yagay.ypower.hook.DetectionRuleIds;
import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.DiagnosticStatus;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.util.ShellEscaper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiagnosticEngine {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private DiagnosticEngine() {}

    public interface Callback {
        void onComplete(DiagnosticReport report);
    }

    public static void runAsync(
            Context context,
            String packageName,
            DiagnosticLevel level,
            long startMs,
            long endMs,
            Callback callback
    ) {
        runAsync(context, packageName, level, "", startMs, endMs, callback);
    }

    public static void runAsync(
            Context context,
            String packageName,
            DiagnosticLevel level,
            String sessionId,
            long startMs,
            long endMs,
            Callback callback
    ) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> callback.onComplete(
                run(app, packageName, level, sessionId, startMs, endMs)
        ));
    }

    public static DiagnosticReport run(
            Context context,
            String packageName,
            DiagnosticLevel level,
            long startMs,
            long endMs
    ) {
        return run(context, packageName, level, "", startMs, endMs);
    }

    public static DiagnosticReport run(
            Context context,
            String packageName,
            DiagnosticLevel level,
            String sessionId,
            long startMs,
            long endMs
    ) {
        DiagnosticReport report = new DiagnosticReport(packageName, level);
        report.sessionId = sessionId == null ? "" : sessionId;
        report.sessionStartMs = startMs;
        report.sessionEndMs = endMs > 0 ? endMs : System.currentTimeMillis();

        collectExitInfo(context, report);
        collectRuntimeTrace(report);

        if (level == DiagnosticLevel.DEEP) {
            collectTargetLogcat(report);
        }

        CorrelationEngine.analyze(report);
        FixRecommendationEngine.apply(report);
        return report;
    }

    private static void collectExitInfo(Context context, DiagnosticReport report) {
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            if (am == null) return;

            List<ApplicationExitInfo> infos =
                    am.getHistoricalProcessExitReasons(report.packageName, 0, 20);

            for (ApplicationExitInfo info : infos) {
                long ts = info.getTimestamp();
                if (ts < report.sessionStartMs || ts > report.sessionEndMs) continue;

                String title = exitTitle(info.getReason());
                boolean attributionExit = isAttributionExit(info.getReason());

                DiagnosticFinding finding = new DiagnosticFinding(
                        "runtime.exit." + info.getReason() + "." + ts,
                        "exit",
                        title,
                        isAbnormalExit(info.getReason())
                                ? DiagnosticStatus.FAIL
                                : DiagnosticStatus.DETECTED,
                        exitSummary(info)
                );
                finding.ruleId = "PROCESS_EXIT_REASON_" + info.getReason();
                finding.correlationScore = attributionExit ? 100 : 60;
                finding.closestEventTimestamp = ts;
                finding.pid = info.getPid();
                finding.evidence(
                        formatTime(ts)
                                + " reason=" + info.getReason()
                                + " status=" + info.getStatus()
                                + " pid=" + info.getPid()
                                + " importance=" + info.getImportance()
                                + " description=" + safe(info.getDescription())
                );

                report.findings.add(finding);
                report.raw.add("[exit-info] " + finding.evidence.get(0));
                report.observedEventCount++;

                if (attributionExit && ts >= report.lastExitTimestamp) {
                    report.lastExitTimestamp = ts;
                    report.exitPid = info.getPid();
                    report.exitRuleId = finding.ruleId;
                    report.exitSource = "ApplicationExitInfo";
                }
            }
        } catch (Throwable ignored) {
            // Runtime-only diagnostics intentionally do not add non-event rows.
        }
    }

    private static void collectRuntimeTrace(DiagnosticReport report) {
        if (!RootShell.isRootAvailable()) return;

        int lineCount = report.level == DiagnosticLevel.DEEP
                ? 12000
                : report.level == DiagnosticLevel.STANDARD ? 8000 : 4000;

        String out = RootShell.exec(
                "logcat -d -v epoch -t " + lineCount
                        + " | grep -F " + ShellEscaper.q("YPowerTrace")
                        + " || true"
        ).text();

        if (out.isBlank()) return;

        List<String> lines = List.of(out.split("\\R"));
        List<LogEventParser.TraceEvent> events = LogEventParser.parse(
                lines,
                report.packageName,
                report.sessionId,
                report.sessionStartMs,
                report.sessionEndMs
        );
        events.sort(Comparator.comparingLong(e -> e.ts));

        // Prefer the exact Java-side exit event because it has TID + stack.
        for (LogEventParser.TraceEvent event : events) {
            if (!"exit".equals(event.type)) continue;
            if (event.ts >= report.lastExitTimestamp) {
                report.lastExitTimestamp = event.ts;
                report.exitPid = event.pid;
                report.exitTid = event.tid;
                report.exitThread = event.thread;
                report.exitSource = event.source;
                report.exitStack = event.stack;
                report.exitRuleId = event.ruleId;
            }
        }

        Map<String, DiagnosticFinding> findings = new LinkedHashMap<>();

        for (LogEventParser.TraceEvent event : events) {
            if ("module".equals(event.type) || "provider".equals(event.type)) continue;

            report.observedEventCount++;
            report.raw.add(toRaw(event));

            EventDescriptor descriptor = describe(event);
            if (descriptor == null) continue;

            String stableRule = event.ruleId == null || event.ruleId.isBlank()
                    ? DetectionRuleIds.UNKNOWN
                    : event.ruleId;

            String key = "exit".equals(event.type)
                    ? "exit|" + stableRule
                    : stableRule + "|" + descriptor.category;

            DiagnosticFinding finding = findings.get(key);
            if (finding == null) {
                finding = new DiagnosticFinding(
                        "runtime." + stableRule,
                        descriptor.category,
                        descriptor.title,
                        descriptor.status,
                        descriptor.summary
                );
                finding.ruleId = stableRule;
                findings.put(key, finding);
            }

            finding.totalCount++;
            if (event.matched) finding.matchedCount++;

            int evidenceLimit = report.level == DiagnosticLevel.QUICK
                    ? 6
                    : report.level == DiagnosticLevel.STANDARD ? 16 : 40;

            if (finding.evidence.size() < evidenceLimit) {
                finding.evidence(formatEvidence(event));
            }

            if ("exit".equals(event.type)) {
                finding.correlationScore = 100;
            }

            if (shouldUseAsRepresentative(event, report.lastExitTimestamp, finding)) {
                copyRepresentative(event, report.lastExitTimestamp, finding);
            }
        }

        for (DiagnosticFinding finding : findings.values()) {
            finding.summary = finding.summary
                    + "；本次运行触发 " + finding.totalCount + " 次"
                    + (finding.matchedCount > 0
                    ? "，其中明确命中 " + finding.matchedCount + " 次"
                    : "");
            report.findings.add(finding);
        }
    }

    private static boolean shouldUseAsRepresentative(
            LogEventParser.TraceEvent event,
            long exitTs,
            DiagnosticFinding finding
    ) {
        if (exitTs <= 0) {
            return event.ts >= finding.closestEventTimestamp;
        }

        long delta = exitTs - event.ts;
        if (delta < 0) return false;

        return finding.closestDeltaMs == Long.MAX_VALUE || delta < finding.closestDeltaMs;
    }

    private static void copyRepresentative(
            LogEventParser.TraceEvent event,
            long exitTs,
            DiagnosticFinding finding
    ) {
        finding.closestEventTimestamp = event.ts;
        finding.closestDeltaMs = exitTs > 0
                ? Math.max(0L, exitTs - event.ts)
                : Long.MAX_VALUE;
        finding.pid = event.pid;
        finding.tid = event.tid;
        finding.thread = event.thread;
        finding.source = event.source;
        finding.input = event.input;
        finding.result = event.result;
        finding.exception = event.exception;
        finding.durationNs = event.durationNs;
        finding.stack = event.stack;
    }

    private static String formatEvidence(LogEventParser.TraceEvent event) {
        StringBuilder b = new StringBuilder();
        b.append(formatTime(event.ts))
                .append(" rule=").append(event.ruleId)
                .append(" source=").append(event.source)
                .append(" pid=").append(event.pid)
                .append(" tid=").append(event.tid)
                .append(" thread=").append(event.thread)
                .append("\n  input: ").append(event.input);

        if (event.result != null && !event.result.isBlank()) {
            b.append("\n  result: ").append(event.result);
        }

        b.append("\n  matched: ").append(event.matched);

        if (event.exception != null && !event.exception.isBlank()) {
            b.append("\n  exception: ").append(event.exception);
        }

        if (event.durationNs > 0) {
            b.append("\n  durationNs: ").append(event.durationNs);
        }

        if (event.stack != null && !event.stack.isBlank()) {
            b.append("\n  stack: ").append(event.stack);
        }

        return b.toString();
    }

    private static EventDescriptor describe(LogEventParser.TraceEvent event) {
        String rule = event.ruleId == null ? DetectionRuleIds.UNKNOWN : event.ruleId;

        switch (rule) {
            case DetectionRuleIds.ROOT_FILE_SU:
                return detected("root", "su 二进制文件检测",
                        "目标 App 实际检查了 su 路径");
            case DetectionRuleIds.ROOT_FILE_MAGISK:
                return detected("root", "Magisk 文件/路径检测",
                        "目标 App 实际检查了 Magisk 相关路径");
            case DetectionRuleIds.ROOT_FILE_KERNELSU:
                return detected("root", "KernelSU 文件/路径检测",
                        "目标 App 实际检查了 KernelSU 相关路径");
            case DetectionRuleIds.ROOT_FILE_APATCH:
                return detected("root", "APatch 文件/路径检测",
                        "目标 App 实际检查了 APatch 相关路径");
            case DetectionRuleIds.ROOT_DATA_ADB:
                return detected("mount", "/data/adb 环境检测",
                        "目标 App 实际访问了 /data/adb");
            case DetectionRuleIds.HOOK_PROC_MAPS:
                return detected("hook", "/proc/self/maps 注入环境检测",
                        "目标 App 实际读取或检查了自身 maps");
            case DetectionRuleIds.DEBUG_PROC_STATUS:
                return detected("debugger", "/proc/self/status 调试状态检测",
                        "目标 App 实际读取了自身 status/TracerPid 相关信息");
            case DetectionRuleIds.MOUNT_PROC_MOUNT:
                return detected("mount", "Mount namespace 检测",
                        "目标 App 实际读取了 mount/mountinfo");
            case DetectionRuleIds.PACKAGE_MAGISK:
                return detected("package", "Magisk 包名检测",
                        "目标 App 实际查询了 Magisk 相关包");
            case DetectionRuleIds.PACKAGE_KERNELSU:
                return detected("package", "KernelSU 包名检测",
                        "目标 App 实际查询了 KernelSU 相关包");
            case DetectionRuleIds.PACKAGE_APATCH:
                return detected("package", "APatch 包名检测",
                        "目标 App 实际查询了 APatch 相关包");
            case DetectionRuleIds.PACKAGE_LSPOSED:
                return detected("package", "LSPosed 包名检测",
                        "目标 App 实际查询了 LSPosed 相关包");
            case DetectionRuleIds.PACKAGE_XPOSED:
                return detected("package", "Xposed 包名检测",
                        "目标 App 实际查询了 Xposed 相关包");
            case DetectionRuleIds.PACKAGE_FRIDA:
                return detected("package", "Frida 包/组件检测",
                        "目标 App 实际查询了 Frida 相关信息");
            case DetectionRuleIds.PACKAGE_SHIZUKU:
                return detected("package", "Shizuku 包名检测",
                        "目标 App 实际查询了 Shizuku 相关包");
            case DetectionRuleIds.PACKAGE_ENUMERATION:
                return detected("package", "已安装应用枚举",
                        "目标 App 实际枚举了已安装应用");
            case DetectionRuleIds.PROP_VERIFIED_BOOT:
                return detected("integrity", "Verified Boot 状态检测",
                        "目标 App 实际读取了 verified boot 状态");
            case DetectionRuleIds.PROP_VBMETA_STATE:
                return detected("integrity", "VBMeta 状态检测",
                        "目标 App 实际读取了 vbmeta device state");
            case DetectionRuleIds.PROP_FLASH_LOCKED:
                return detected("integrity", "Bootloader Lock 状态检测",
                        "目标 App 实际读取了 flash locked 状态");
            case DetectionRuleIds.PROP_DEBUGGABLE:
                return detected("environment", "ro.debuggable 检测",
                        "目标 App 实际读取了 ro.debuggable");
            case DetectionRuleIds.PROP_SECURE:
                return detected("environment", "ro.secure 检测",
                        "目标 App 实际读取了 ro.secure");
            case DetectionRuleIds.PROP_BUILD_TAGS:
                return detected("environment", "Build Tags 检测",
                        "目标 App 实际读取了 ro.build.tags");
            case DetectionRuleIds.PROP_BUILD_TYPE:
                return detected("environment", "Build Type 检测",
                        "目标 App 实际读取了 ro.build.type");
            case DetectionRuleIds.CMD_SU:
                return detected("root", "su 命令检测",
                        "目标 App 实际执行了 su 相关命令");
            case DetectionRuleIds.CMD_GETPROP:
                return detected("environment", "getprop 环境查询",
                        "目标 App 实际通过 shell 查询系统属性");
            case DetectionRuleIds.CMD_MOUNT:
                return detected("mount", "mount 命令检测",
                        "目标 App 实际执行了 mount 相关命令");
            case DetectionRuleIds.CMD_SELINUX:
                return detected("selinux", "SELinux 状态检测",
                        "目标 App 实际执行了 getenforce");
            case DetectionRuleIds.DEBUG_IS_CONNECTED:
                return detected("debugger", "Debugger 连接检测",
                        "目标 App 实际调用了 Debug.isDebuggerConnected");
            case DetectionRuleIds.DEBUG_WAITING:
                return detected("debugger", "Debugger 等待状态检测",
                        "目标 App 实际调用了 Debug.waitingForDebugger");
            case DetectionRuleIds.PERMISSION_QUERY:
                return detected("permission", "权限状态查询",
                        "目标 App 实际查询了权限状态");
            case DetectionRuleIds.EXIT_SYSTEM:
                return failed("exit", "System.exit 主动退出",
                        "目标 App 实际调用了 System.exit");
            case DetectionRuleIds.EXIT_HALT:
                return failed("exit", "Runtime.halt 主动退出",
                        "目标 App 实际调用了 Runtime.halt");
            case DetectionRuleIds.EXIT_KILL_PROCESS:
                return failed("exit", "killProcess 主动退出",
                        "目标 App 实际调用了 Process.killProcess");
            default:
                return describeByType(event);
        }
    }

    private static EventDescriptor describeByType(LogEventParser.TraceEvent event) {
        switch (event.type) {
            case "file":
                return detected("file", "敏感文件 / proc 检测",
                        "目标 App 实际访问了诊断规则命中的敏感路径");
            case "package":
                return detected("package", "包/安装环境查询",
                        "目标 App 实际查询了安装包或安装来源");
            case "property":
                return detected("environment", "系统属性检测",
                        "目标 App 实际读取了环境相关系统属性");
            case "exec":
                return detected("command", "敏感命令检测",
                        "目标 App 实际执行了诊断规则命中的命令");
            case "debugger":
                return detected("debugger", "调试器检测",
                        "目标 App 实际查询了调试器状态");
            case "permission":
                return detected("permission", "权限状态查询",
                        "目标 App 实际查询了权限状态");
            case "exit":
                return failed("exit", "应用主动退出调用",
                        "目标 App 实际调用了主动退出 API");
            default:
                return null;
        }
    }

    private static EventDescriptor detected(String category, String title, String summary) {
        return new EventDescriptor(category, title, DiagnosticStatus.DETECTED, summary);
    }

    private static EventDescriptor failed(String category, String title, String summary) {
        return new EventDescriptor(category, title, DiagnosticStatus.FAIL, summary);
    }

    private static void collectTargetLogcat(DiagnosticReport report) {
        if (!RootShell.isRootAvailable()) return;

        String currentPid = RootShell.exec(
                "pidof " + ShellEscaper.q(report.packageName) + " || true"
        ).text().trim();

        StringBuilder pattern = new StringBuilder(report.packageName);
        if (!currentPid.isBlank()) {
            for (String pid : currentPid.split("\\s+")) {
                if (!pid.isBlank()) pattern.append('|').append(pid);
            }
        }
        pattern.append("|AndroidRuntime|Fatal signal|ANR in|avc: denied|lmkd");

        String out = RootShell.exec(
                "logcat -d -v epoch -t 7000 | grep -Ei "
                        + ShellEscaper.q(pattern.toString())
                        + " || true"
        ).text();

        if (out.isBlank()) return;

        long startSec = report.sessionStartMs / 1000L;
        long endSec = report.sessionEndMs / 1000L;
        int kept = 0;

        for (String line : out.split("\\R")) {
            long epoch = parseEpochSeconds(line);
            if (epoch > 0 && (epoch < startSec || epoch > endSec)) continue;

            if (!line.contains(report.packageName)
                    && currentPid.isBlank()
                    && !line.contains("AndroidRuntime")) {
                continue;
            }

            if (kept++ >= 180) break;
            report.raw.add("[logcat] " + line);
        }
    }

    private static boolean isAttributionExit(int reason) {
        return isAbnormalExit(reason) || reason == ApplicationExitInfo.REASON_EXIT_SELF;
    }

    private static boolean isAbnormalExit(int reason) {
        return reason == ApplicationExitInfo.REASON_CRASH
                || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                || reason == ApplicationExitInfo.REASON_ANR
                || reason == ApplicationExitInfo.REASON_LOW_MEMORY
                || reason == ApplicationExitInfo.REASON_SIGNALED
                || reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
                || reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE;
    }

    private static String exitTitle(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH:
                return "Java / Runtime Crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "Native Crash";
            case ApplicationExitInfo.REASON_ANR:
                return "ANR";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "低内存结束进程";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "Signal 结束进程";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "资源使用异常结束";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "初始化失败";
            case ApplicationExitInfo.REASON_EXIT_SELF:
                return "应用自行退出";
            default:
                return "进程退出";
        }
    }

    private static String exitSummary(ApplicationExitInfo info) {
        return exitTitle(info.getReason())
                + "；status=" + info.getStatus()
                + (info.getDescription() == null ? "" : "；" + info.getDescription());
    }

    private static String toRaw(LogEventParser.TraceEvent event) {
        return "[trace]"
                + " ts=" + event.ts
                + " session=" + event.sessionId
                + " package=" + event.packageName
                + " type=" + event.type
                + " ruleId=" + event.ruleId
                + " source=" + event.source
                + " pid=" + event.pid
                + " tid=" + event.tid
                + " thread=" + event.thread
                + " input=" + event.input
                + " result=" + event.result
                + " matched=" + event.matched
                + " exception=" + event.exception
                + " durationNs=" + event.durationNs
                + (event.stack == null || event.stack.isBlank()
                ? ""
                : " stack=" + event.stack);
    }

    private static long parseEpochSeconds(String line) {
        try {
            int space = line.indexOf(' ');
            String first = space > 0 ? line.substring(0, space) : line;
            return (long) Double.parseDouble(first);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
                .format(new Date(timestamp));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class EventDescriptor {
        final String category;
        final String title;
        final DiagnosticStatus status;
        final String summary;

        EventDescriptor(
                String category,
                String title,
                DiagnosticStatus status,
                String summary
        ) {
            this.category = category;
            this.title = title;
            this.status = status;
            this.summary = summary;
        }
    }
}
