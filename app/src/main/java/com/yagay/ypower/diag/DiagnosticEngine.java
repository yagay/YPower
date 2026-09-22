package com.yagay.ypower.diag;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.DiagnosticStatus;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.util.ShellEscaper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> callback.onComplete(run(app, packageName, level, startMs, endMs)));
    }

    public static DiagnosticReport run(
            Context context,
            String packageName,
            DiagnosticLevel level,
            long startMs,
            long endMs
    ) {
        DiagnosticReport report = new DiagnosticReport(packageName, level);
        report.sessionStartMs = startMs;
        report.sessionEndMs = endMs > 0 ? endMs : System.currentTimeMillis();

        collectExitInfo(context, report);
        collectRuntimeTrace(report);
        if (level == DiagnosticLevel.DEEP) collectTargetLogcat(report);

        CorrelationEngine.analyze(report);
        FixRecommendationEngine.apply(report);
        return report;
    }

    private static void collectExitInfo(Context context, DiagnosticReport report) {
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            if (am == null) return;

            List<ApplicationExitInfo> infos = am.getHistoricalProcessExitReasons(report.packageName, 0, 20);
            for (ApplicationExitInfo info : infos) {
                long ts = info.getTimestamp();
                if (ts < report.sessionStartMs || ts > report.sessionEndMs) continue;

                report.lastExitTimestamp = Math.max(report.lastExitTimestamp, ts);
                String title = exitTitle(info.getReason());
                DiagnosticFinding finding = new DiagnosticFinding(
                        "runtime.exit." + info.getReason() + "." + ts,
                        "exit",
                        title,
                        isAbnormalExit(info.getReason()) ? DiagnosticStatus.FAIL : DiagnosticStatus.DETECTED,
                        exitSummary(info)
                );
                finding.correlationScore = isAbnormalExit(info.getReason()) ? 100 : 70;
                finding.evidence(formatTime(ts) + " reason=" + info.getReason()
                        + " status=" + info.getStatus()
                        + " pid=" + info.getPid()
                        + " importance=" + info.getImportance()
                        + " description=" + safe(info.getDescription()));
                report.findings.add(finding);
                report.raw.add("[exit-info] " + finding.evidence.get(0));
                report.observedEventCount++;
            }
        } catch (Throwable ignored) {
            // Runtime-only diagnostics intentionally do not add non-event rows.
        }
    }

    private static void collectRuntimeTrace(DiagnosticReport report) {
        if (!RootShell.isRootAvailable()) return;

        int lineCount = report.level == DiagnosticLevel.DEEP ? 10000
                : report.level == DiagnosticLevel.STANDARD ? 6000 : 3000;

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
                report.sessionStartMs,
                report.sessionEndMs
        );

        Map<String, DiagnosticFinding> findings = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (LogEventParser.TraceEvent event : events) {
            if ("module".equals(event.type) || "provider".equals(event.type)) continue;

            report.observedEventCount++;
            report.raw.add(toRaw(event));

            EventDescriptor descriptor = describe(event);
            if (descriptor == null) continue;

            String key = descriptor.category + "|" + descriptor.title;
            DiagnosticFinding finding = findings.get(key);
            if (finding == null) {
                finding = new DiagnosticFinding(
                        "runtime." + Math.abs(key.hashCode()),
                        descriptor.category,
                        descriptor.title,
                        descriptor.status,
                        descriptor.summary
                );
                findings.put(key, finding);
                counts.put(key, 0);
            }

            int count = counts.get(key) + 1;
            counts.put(key, count);

            int evidenceLimit = report.level == DiagnosticLevel.QUICK ? 4
                    : report.level == DiagnosticLevel.STANDARD ? 12 : 30;
            if (finding.evidence.size() < evidenceLimit) {
                String evidence = formatTime(event.ts) + " "
                        + event.source + " → " + event.value;
                if (report.level != DiagnosticLevel.QUICK && event.stack != null && !event.stack.isBlank()) {
                    evidence += "\n  stack: " + event.stack;
                }
                finding.evidence(evidence);
            }

            if ("exit".equals(event.type)) {
                report.lastExitTimestamp = Math.max(report.lastExitTimestamp, event.ts);
                finding.correlationScore = 100;
            }
        }

        for (Map.Entry<String, DiagnosticFinding> entry : findings.entrySet()) {
            DiagnosticFinding finding = entry.getValue();
            int count = counts.get(entry.getKey());
            finding.summary = finding.summary + "；本次运行触发 " + count + " 次";
            if (finding.correlationScore == 0 && report.lastExitTimestamp > 0) {
                finding.correlationScore = scoreFromEvidenceTime(finding, report.lastExitTimestamp);
            }
            report.findings.add(finding);
        }
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
                "logcat -d -v epoch -t 5000 | grep -Ei "
                        + ShellEscaper.q(pattern.toString())
                        + " || true"
        ).text();

        if (out.isBlank()) return;

        long startSec = report.sessionStartMs / 1000L;
        long endSec = (report.sessionEndMs + 3000L) / 1000L;
        int kept = 0;
        for (String line : out.split("\\R")) {
            long epoch = parseEpochSeconds(line);
            if (epoch > 0 && (epoch < startSec || epoch > endSec)) continue;
            if (!line.contains(report.packageName)
                    && currentPid.isBlank()
                    && !line.contains("AndroidRuntime")) {
                continue;
            }
            if (kept++ >= 120) break;
            report.raw.add("[logcat] " + line);
        }
    }

    private static EventDescriptor describe(LogEventParser.TraceEvent event) {
        String value = (event.value == null ? "" : event.value).toLowerCase(Locale.ROOT);

        switch (event.type) {
            case "package":
                return new EventDescriptor(
                        "package",
                        "包/安装环境查询",
                        DiagnosticStatus.DETECTED,
                        "目标 App 实际查询了安装包或安装来源信息"
                );

            case "property":
                if (value.contains("verifiedboot") || value.contains("vbmeta")
                        || value.contains("flash.locked") || value.contains("ro.boot.")) {
                    return new EventDescriptor(
                            "integrity",
                            "Bootloader / AVB 属性检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际读取了启动完整性相关系统属性"
                    );
                }
                if (value.contains("ro.debuggable") || value.contains("ro.secure")
                        || value.contains("ro.build.tags") || value.contains("ro.build.type")) {
                    return new EventDescriptor(
                            "environment",
                            "系统构建 / 调试属性检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际读取了调试、secure 或 build 状态"
                    );
                }
                return new EventDescriptor(
                        "environment",
                        "系统属性检测",
                        DiagnosticStatus.DETECTED,
                        "目标 App 实际读取了环境相关系统属性"
                );

            case "file":
                if (value.contains("/proc/self/maps")) {
                    return new EventDescriptor(
                            "hook",
                            "/proc/self/maps 注入环境检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际读取或检查了自身 maps"
                    );
                }
                if (value.contains("/proc/self/status") || value.contains("tracerpid")) {
                    return new EventDescriptor(
                            "debugger",
                            "调试器 / TracerPid 检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际读取了调试状态相关 /proc 信息"
                    );
                }
                if (value.contains("mountinfo") || value.contains("/proc/mount")
                        || value.contains("/data/adb")) {
                    return new EventDescriptor(
                            "mount",
                            "Systemless / Mount 环境检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际检查了 mount 或 /data/adb 环境"
                    );
                }
                if (containsAny(value, "magisk", "kernelsu", "apatch", "/su", "system/bin/su", "system/xbin/su")) {
                    return new EventDescriptor(
                            "root",
                            "Root 文件 / 路径检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际检查了 Root 相关文件或路径"
                    );
                }
                return new EventDescriptor(
                        "file",
                        "敏感文件 / proc 检测",
                        DiagnosticStatus.DETECTED,
                        "目标 App 实际访问了诊断规则命中的敏感路径"
                );

            case "exec":
                if (containsAny(value, "which su", " magisk", "kernelsu", "apatch")) {
                    return new EventDescriptor(
                            "root",
                            "Root 命令检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际执行了 Root 环境相关命令"
                    );
                }
                if (value.contains("getprop")) {
                    return new EventDescriptor(
                            "environment",
                            "Shell 系统属性检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际通过 shell 查询系统属性"
                    );
                }
                if (value.contains("mount") || value.contains("getenforce")) {
                    return new EventDescriptor(
                            "environment",
                            "Mount / SELinux 命令检测",
                            DiagnosticStatus.DETECTED,
                            "目标 App 实际执行了 mount 或 SELinux 环境查询"
                    );
                }
                return new EventDescriptor(
                        "command",
                        "敏感命令检测",
                        DiagnosticStatus.DETECTED,
                        "目标 App 实际执行了诊断规则命中的命令"
                );

            case "permission":
                return new EventDescriptor(
                        "permission",
                        "权限状态查询",
                        DiagnosticStatus.DETECTED,
                        "目标 App 实际查询了 YPower 正在观察的权限状态"
                );

            case "exit":
                return new EventDescriptor(
                        "exit",
                        "应用主动退出调用",
                        DiagnosticStatus.FAIL,
                        "目标 App 实际调用了 System.exit / Runtime.halt / killProcess"
                );

            default:
                return null;
        }
    }

    private static int scoreFromEvidenceTime(DiagnosticFinding finding, long exitTs) {
        int best = 0;
        for (String evidence : finding.evidence) {
            long ts = parseEvidenceTime(evidence, exitTs);
            if (ts <= 0) continue;
            long delta = exitTs - ts;
            int score;
            if (delta < 0) score = 0;
            else if (delta <= 100) score = 90;
            else if (delta <= 500) score = 75;
            else if (delta <= 1500) score = 60;
            else if (delta <= 5000) score = 40;
            else score = 15;
            best = Math.max(best, score);
        }
        return best;
    }

    private static long parseEvidenceTime(String evidence, long referenceDayMs) {
        try {
            String hhmmss = evidence.substring(0, 12);
            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
            Date parsed = f.parse(hhmmss);
            if (parsed == null) return 0;
            java.util.Calendar ref = java.util.Calendar.getInstance();
            ref.setTimeInMillis(referenceDayMs);
            java.util.Calendar t = java.util.Calendar.getInstance();
            t.setTime(parsed);
            ref.set(java.util.Calendar.HOUR_OF_DAY, t.get(java.util.Calendar.HOUR_OF_DAY));
            ref.set(java.util.Calendar.MINUTE, t.get(java.util.Calendar.MINUTE));
            ref.set(java.util.Calendar.SECOND, t.get(java.util.Calendar.SECOND));
            ref.set(java.util.Calendar.MILLISECOND, t.get(java.util.Calendar.MILLISECOND));
            return ref.getTimeInMillis();
        } catch (Exception ignored) {
            return 0;
        }
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
        return "[trace] ts=" + event.ts
                + " package=" + event.packageName
                + " type=" + event.type
                + " source=" + event.source
                + " value=" + event.value
                + (event.stack == null || event.stack.isBlank() ? "" : " stack=" + event.stack);
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
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(timestamp));
    }

    private static boolean containsAny(String s, String... values) {
        for (String value : values) if (s.contains(value)) return true;
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class EventDescriptor {
        final String category;
        final String title;
        final DiagnosticStatus status;
        final String summary;

        EventDescriptor(String category, String title, DiagnosticStatus status, String summary) {
            this.category = category;
            this.title = title;
            this.status = status;
            this.summary = summary;
        }
    }
}
