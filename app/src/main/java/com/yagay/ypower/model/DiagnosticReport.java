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
    public String exitSummary = "未发现明确异常退出";
    public String attribution = "当前数据不足以确认唯一触发项";
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
        b.append("退出：").append(exitSummary).append('\n');
        b.append("归因：").append(attribution).append("\n\n");
        for (DiagnosticFinding f : findings) {
            b.append(String.format(Locale.ROOT, "%-18s  %s\n", f.title, f.status.zh));
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
            b.append('\n');
        }
        return b.toString();
    }

    public String rawText() {
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
            o.put("createdAtText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date(createdAt)));
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
}
