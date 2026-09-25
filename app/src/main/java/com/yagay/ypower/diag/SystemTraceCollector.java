package com.yagay.ypower.diag;

import android.content.Context;

import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.util.ShellEscaper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public final class SystemTraceCollector {
    private static final int MAX_DURATION_SEC = 600;

    private SystemTraceCollector() {}

    public static CaptureState start(
            Context context,
            String packageName,
            String sessionId,
            DiagnosticLevel level
    ) {
        CaptureState state = new CaptureState();
        state.sessionId = sessionId == null ? "" : sessionId;
        if (level != DiagnosticLevel.DEEP || !RootShell.isRootAvailable()) {
            return state;
        }

        String safe = safeId(sessionId);
        state.tempDir = "/data/local/tmp/ypower/" + safe;
        state.perfettoKey = "ypower_" + safe;
        state.perfettoTempPath = state.tempDir + "/session.perfetto-trace";
        state.perfettoConfigPath = state.tempDir + "/perfetto.pbtxt";
        state.simpleperfTempPath = state.tempDir + "/perf.data";
        state.simpleperfLogPath = state.tempDir + "/simpleperf.log";

        RootShell.exec("mkdir -p " + ShellEscaper.q(state.tempDir) + " && chmod 700 "
                + ShellEscaper.q(state.tempDir));

        state.perfettoAvailable = commandExists("perfetto");
        if (state.perfettoAvailable) {
            String config = perfettoConfig(packageName);
            RootShell.exec("printf %s " + ShellEscaper.q(config)
                    + " > " + ShellEscaper.q(state.perfettoConfigPath));

            RootShell.CommandResult start = RootShell.exec(
                    "perfetto --txt -c " + ShellEscaper.q(state.perfettoConfigPath)
                            + " --detach=" + ShellEscaper.q(state.perfettoKey)
                            + " -o " + ShellEscaper.q(state.perfettoTempPath)
                            + " 2>&1"
            );
            state.perfettoStarted = start.ok();
            state.perfettoStartMessage = start.text();
        }

        state.simpleperfAvailable = commandExists("simpleperf");
        if (state.simpleperfAvailable) {
            String cmd = "nohup simpleperf record --app "
                    + ShellEscaper.q(packageName)
                    + " -e task-clock -f 99 -g --duration " + MAX_DURATION_SEC
                    + " -o " + ShellEscaper.q(state.simpleperfTempPath)
                    + " > " + ShellEscaper.q(state.simpleperfLogPath)
                    + " 2>&1 </dev/null & echo $!";
            RootShell.CommandResult start = RootShell.exec(cmd);
            state.simpleperfPid = parsePid(start.text());
            state.simpleperfStarted = state.simpleperfPid > 0;
            state.simpleperfStartMessage = start.text();
        }

        return state;
    }

    public static CaptureState stop(Context context, CaptureState state) {
        if (state == null) return new CaptureState();

        if (state.perfettoStarted && !state.perfettoKey.isBlank()) {
            RootShell.CommandResult stop = RootShell.exec(
                    "perfetto --attach=" + ShellEscaper.q(state.perfettoKey)
                            + " --stop 2>&1 || true"
            );
            state.perfettoStopMessage = stop.text();
        }

        if (state.simpleperfStarted && state.simpleperfPid > 0) {
            RootShell.exec(
                    "kill -INT " + state.simpleperfPid + " 2>/dev/null || true",
                    "i=0; while kill -0 " + state.simpleperfPid
                            + " 2>/dev/null && [ $i -lt 30 ]; do sleep 0.1; i=$((i+1)); done",
                    "kill -TERM " + state.simpleperfPid + " 2>/dev/null || true"
            );
        }

        File dir = new File(context.getFilesDir(), "diagnostics/" + safeId(state.sessionId));
        if (!dir.exists()) dir.mkdirs();

        int uid = android.os.Process.myUid();

        if (state.perfettoStarted && !state.perfettoTempPath.isBlank()) {
            File out = new File(dir, "session.perfetto-trace");
            copyAsApp(state.perfettoTempPath, out, uid);
            if (out.exists() && out.length() > 0) {
                state.perfettoOutputPath = out.getAbsolutePath();
                state.perfettoBytes = out.length();
            }
        }

        if (state.simpleperfStarted && !state.simpleperfTempPath.isBlank()) {
            File data = new File(dir, "perf.data");
            copyAsApp(state.simpleperfTempPath, data, uid);
            if (data.exists() && data.length() > 0) {
                state.simpleperfOutputPath = data.getAbsolutePath();
                state.simpleperfBytes = data.length();

                File report = new File(dir, "simpleperf-report.txt");
                String command = "simpleperf report -i "
                        + ShellEscaper.q(state.simpleperfTempPath)
                        + " -g --sort comm,pid,tid,dso,symbol 2>&1 | head -n 500";
                RootShell.CommandResult result = RootShell.exec(command);

                try {
                    Files.writeString(
                            report.toPath(),
                            result.text(),
                            StandardCharsets.UTF_8
                    );
                    state.simpleperfReportPath = report.getAbsolutePath();
                    state.simpleperfSummary = result.text();
                } catch (Exception ignored) {
                    state.simpleperfSummary = result.text();
                }
            }
        }

        if (!state.tempDir.isBlank()) {
            RootShell.exec("rm -rf " + ShellEscaper.q(state.tempDir) + " || true");
        }

        return state;
    }

    public static void attachToReport(Context context, DiagnosticReport report) {
        File dir = new File(context.getFilesDir(), "diagnostics/" + safeId(report.sessionId));
        File perfetto = new File(dir, "session.perfetto-trace");
        File perfData = new File(dir, "perf.data");
        File perfReport = new File(dir, "simpleperf-report.txt");

        report.perfettoTracePath = perfetto.exists() ? perfetto.getAbsolutePath() : "";
        report.perfettoTraceBytes = perfetto.exists() ? perfetto.length() : 0L;
        report.simpleperfDataPath = perfData.exists() ? perfData.getAbsolutePath() : "";
        report.simpleperfDataBytes = perfData.exists() ? perfData.length() : 0L;
        report.simpleperfReportPath = perfReport.exists() ? perfReport.getAbsolutePath() : "";

        if (perfReport.exists()) {
            try {
                String text = Files.readString(perfReport.toPath(), StandardCharsets.UTF_8);
                report.simpleperfSummary = limitLines(text, 120);
                report.raw.add("[simpleperf]\n" + report.simpleperfSummary);
            } catch (Exception ignored) {
            }
        }

        if (perfetto.exists()) {
            report.raw.add("[perfetto] trace="
                    + perfetto.getAbsolutePath()
                    + " bytes=" + perfetto.length());
        }
    }

    private static String perfettoConfig(String packageName) {
        return "duration_ms: " + (MAX_DURATION_SEC * 1000L) + "\n"
                + "write_into_file: true\n"
                + "file_write_period_ms: 1000\n"
                + "buffers { size_kb: 32768 fill_policy: RING_BUFFER }\n"
                + "buffers { size_kb: 4096 fill_policy: RING_BUFFER }\n"
                + "data_sources { config { name: \"linux.ftrace\" ftrace_config {\n"
                + "  ftrace_events: \"sched/sched_switch\"\n"
                + "  ftrace_events: \"sched/sched_waking\"\n"
                + "  ftrace_events: \"sched/sched_process_exit\"\n"
                + "  atrace_categories: \"am\"\n"
                + "  atrace_categories: \"wm\"\n"
                + "  atrace_categories: \"pm\"\n"
                + "  atrace_categories: \"dalvik\"\n"
                + "  atrace_categories: \"binder_driver\"\n"
                + "  atrace_apps: \"" + escapePbtxt(packageName) + "\"\n"
                + "} } }\n"
                + "data_sources { config { name: \"linux.process_stats\" target_buffer: 1 "
                + "process_stats_config { scan_all_processes_on_start: true proc_stats_poll_ms: 1000 } } }\n";
    }

    private static void copyAsApp(String source, File out, int uid) {
        RootShell.exec(
                "cp " + ShellEscaper.q(source) + " " + ShellEscaper.q(out.getAbsolutePath())
                        + " 2>/dev/null || true",
                "chown " + uid + ":" + uid + " " + ShellEscaper.q(out.getAbsolutePath())
                        + " 2>/dev/null || true",
                "chmod 600 " + ShellEscaper.q(out.getAbsolutePath())
                        + " 2>/dev/null || true"
        );
    }

    private static boolean commandExists(String command) {
        return RootShell.exec("command -v " + command + " >/dev/null 2>&1").ok();
    }

    private static int parsePid(String text) {
        if (text == null) return -1;
        for (String part : text.trim().split("\\s+")) {
            try {
                int pid = Integer.parseInt(part);
                if (pid > 0) return pid;
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static String limitLines(String text, int max) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\\R");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length && i < max; i++) {
            out.append(lines[i]).append('\n');
        }
        return out.toString().trim();
    }

    private static String safeId(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String escapePbtxt(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class CaptureState {
        public String sessionId = "";
        public String tempDir = "";

        public boolean perfettoAvailable;
        public boolean perfettoStarted;
        public String perfettoKey = "";
        public String perfettoConfigPath = "";
        public String perfettoTempPath = "";
        public String perfettoOutputPath = "";
        public long perfettoBytes;
        public String perfettoStartMessage = "";
        public String perfettoStopMessage = "";

        public boolean simpleperfAvailable;
        public boolean simpleperfStarted;
        public int simpleperfPid = -1;
        public String simpleperfTempPath = "";
        public String simpleperfLogPath = "";
        public String simpleperfOutputPath = "";
        public String simpleperfReportPath = "";
        public long simpleperfBytes;
        public String simpleperfStartMessage = "";
        public String simpleperfSummary = "";
    }
}
