package com.yagay.ypower.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiagnosticReport {
    public String packageName;
    public DiagnosticLevel level;
    public long createdAt = System.currentTimeMillis();
    public long sessionStartMs;
    public long sessionEndMs;
    public long lastExitTimestamp;
    public int observedEventCount;
    public String exitSummary = "";
    public String attribution = "";
    public final List<DiagnosticFinding> findings = new ArrayList<>();
    public final List<String> raw = new ArrayList<>();

    public DiagnosticReport(String packageName, DiagnosticLevel level) {
        this.packageName = packageName;
        this.level = level;
    }

    public String simpleText() {
        StringBuilder b = new StringBuilder();
        b.append("应用：").append(packageName).append('\n');
        b.append("级别：").append(level).append('\n');
        if (sessionStartMs > 0) {
            b.append("运行会话：")
                    .append(formatTime(sessionStartMs))
                    .append(" - ")
                    .append(sessionEndMs > 0 ? formatTime(sessionEndMs) : "进行中")
                    .append('\n');
        }
        b.append("实际观察事件：").append(observedEventCount).append("\n\n");

        if (findings.isEmpty()) {
            b.append("本次运行未观察到可识别的检测或异常事件。\n");
            return b.toString();
        }

        if (!exitSummary.isBlank()) b.append("退出：").append(exitSummary).append('\n');
        if (!attribution.isBlank()) b.append("归因：").append(attribution).append('\n');
        if (!exitSummary.isBlank() || !attribution.isBlank()) b.append('\n');

        for (DiagnosticFinding finding : findings) {
            b.append("• ").append(finding.title);
            if (finding.correlationScore > 0) {
                b.append("  关联 ").append(finding.correlationScore).append("/100");
            }
            b.append('\n');
        }
        return b.toString();
    }

    public String detailedText() {
        StringBuilder b = new StringBuilder(simpleText()).append('\n');
        List<DiagnosticFinding> ordered = new ArrayList<>(findings);
        ordered.sort(Comparator.comparingInt((DiagnosticFinding f) -> f.correlationScore).reversed());
        for (DiagnosticFinding f : ordered) {
            b.append("[ ").append(f.status.zh).append(" ] ").append(f.title).append('\n');
            b.append("类别：").append(f.category).append('\n');
            b.append("摘要：").append(f.summary).append('\n');
            if (f.correlationScore > 0) b.append("退出关联：").append(f.correlationScore).append("/100\n");
            if (f.detail != null && !f.detail.equals(f.summary)) b.append("详情：").append(f.detail).append('\n');
            for (String e : f.evidence) b.append("证据：").append(e).append('\n');
            for (FixRecommendation recommendation : f.recommendations) {
                b.append("建议：").append(recommendation.title).append('\n');
                b.append("  ").append(recommendation.detail).append('\n');
                b.append("  参考：").append(recommendation.source).append('\n');
            }
            b.append('\n');
        }
        return b.toString();
    }

    public String recommendationText() {
        StringBuilder b = new StringBuilder();
        int count = 0;
        for (DiagnosticFinding finding : findings) {
            for (FixRecommendation recommendation : finding.recommendations) {
                count++;
                b.append(count).append(". ").append(recommendation.title).append('\n');
                b.append("对应：").append(finding.title).append('\n');
                b.append(recommendation.detail).append('\n');
                b.append("参考：").append(recommendation.source).append("\n\n");
            }
        }
        if (count == 0) {
            return "本次运行没有足够证据生成针对性的修复建议。\n";
        }
        return b.toString();
    }

    public String rawText() {
        if (raw.isEmpty()) return "本次运行没有采集到可显示的原始事件。\n";
        StringBuilder b = new StringBuilder();
        for (String line : raw) b.append(line).append('\n');
        return b.toString();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("packageName", packageName);
            o.put("level", level.name());
            o.put("createdAt", createdAt);
            o.put("sessionStartMs", sessionStartMs);
            o.put("sessionEndMs", sessionEndMs);
            o.put("observedEventCount", observedEventCount);
            o.put("lastExitTimestamp", lastExitTimestamp);
            o.put("exitSummary", exitSummary);
            o.put("attribution", attribution);
            JSONArray fs = new JSONArray();
            for (DiagnosticFinding f : findings) fs.put(f.toJson());
            o.put("findings", fs);
            JSONArray rs = new JSONArray();
            for (String r : raw) rs.put(r);
            o.put("raw", rs);
        } catch (JSONException ignored) {
        }
        return o;
    }

    private static String formatTime(long timestamp) {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date(timestamp));
    }
}
