package com.yagay.ypower.diag;

import com.yagay.ypower.model.DetectionHitState;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LogEventParser {
    private LogEventParser() {}

    public static List<TraceEvent> parse(
            List<String> lines,
            String packageName,
            String sessionId,
            long startMs,
            long endMs
    ) {
        List<TraceEvent> events = new ArrayList<>();

        for (String line : lines) {
            if (!line.contains("YPowerTrace")) continue;

            int start = line.indexOf('{');
            if (start < 0) continue;

            try {
                JSONObject o = new JSONObject(line.substring(start));
                TraceEvent e = new TraceEvent();

                e.ts = o.optLong("ts", 0);
                e.packageName = o.optString("package", "");
                e.sessionId = o.optString("sessionId", "");
                e.type = o.optString("type", "unknown");
                e.ruleId = o.optString("ruleId", "UNKNOWN");
                e.input = o.optString("input", o.optString("value", ""));
                e.value = o.optString("value", e.input);
                e.result = o.optString("result", "");
                e.matched = o.optBoolean("matched", false);
                e.exception = o.optString("exception", "");
                String hitState = o.optString("hitState", "");
                e.hitState = hitState.isBlank()
                        ? DetectionRuleCatalog.evaluate(e.ruleId, e.matched, e.result, e.exception)
                        : DetectionHitState.from(hitState, e.matched);
                e.source = o.optString("source", "");
                e.pid = o.optInt("pid", -1);
                e.tid = o.optInt("tid", -1);
                e.thread = o.optString("thread", "");
                e.process = o.optString("process", "");
                e.durationNs = o.optLong("durationNs", 0L);
                e.stack = o.optString("stack", "");

                if (!packageName.equals(e.packageName)) continue;

                // New sessions use an exact session id. Old events remain readable by time window.
                if (sessionId != null && !sessionId.isBlank()
                        && !e.sessionId.isBlank()
                        && !sessionId.equals(e.sessionId)) {
                    continue;
                }

                if (e.ts < startMs) continue;
                if (endMs > 0 && e.ts > endMs) continue;

                events.add(e);
            } catch (Exception ignored) {
            }
        }

        return events;
    }

    public static final class TraceEvent {
        public long ts;
        public String packageName;
        public String sessionId;
        public String type;
        public String ruleId;
        public String input;
        public String value;
        public String result;
        public boolean matched;
        public DetectionHitState hitState = DetectionHitState.UNKNOWN;
        public String exception;
        public String source;
        public int pid;
        public int tid;
        public String thread;
        public String process;
        public long durationNs;
        public String stack;
    }
}
