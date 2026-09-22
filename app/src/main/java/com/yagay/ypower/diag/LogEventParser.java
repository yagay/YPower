package com.yagay.ypower.diag;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LogEventParser {
    private LogEventParser() {}

    public static List<TraceEvent> parse(List<String> lines, String packageName, long startMs, long endMs) {
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
                e.type = o.optString("type", "unknown");
                e.value = o.optString("value", "");
                e.source = o.optString("source", "");
                e.stack = o.optString("stack", "");

                if (!packageName.equals(e.packageName)) continue;
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
        public String type;
        public String value;
        public String source;
        public String stack;
    }
}
