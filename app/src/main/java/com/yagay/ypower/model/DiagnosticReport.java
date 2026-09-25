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
    public String sessionId = "";
    public long lastExitTimestamp;
    public int exitPid = -1;
    public int exitTid = -1;
    public String exitThread = "";
    public String exitSource = "";
    public String exitStack = "";
    public String exitRuleId = "";
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
            b.append("• ");
            if (finding.attributionRank == 1) b.append("[主要归因] ");
            else if (finding.attributionRank == 2) b.append("[次要归因] ");
            b.append(finding.title);
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
            b.append("[ ").append(f.status.zh).append(" ] ");
            if (f.attributionRank == 1) b.append("[主要归因] ");
            else if (f.attributionRank == 2) b.append("[次要归因] ");
            b.append(f.title).append('\n');
            b.append("类别：").append(f.category).append('\n');
            if (f.ruleId != null && !f.ruleId.isBlank()) {
                b.append("规则：").append(f.ruleId).append('\n');
            }
            b.append("摘要：").append(f.summary).append('\n');
            if (f.totalCount > 0) {
                b.append("本次触发：").append(f.totalCount)
                        .append(" 次，命中 ").append(f.matchedCount).append(" 次\n");
            }
            if (f.closestDeltaMs != Long.MAX_VALUE) {
                b.append("距退出：").append(f.closestDeltaMs).append(" ms\n");
            }
            if (f.tid >= 0) {
                b.append("线程：").append(f.thread)
                        .append(" (pid=").append(f.pid)
                        .append(", tid=").append(f.tid).append(")\n");
            }
            if (f.sameThreadAsExit) b.append("与退出：同线程\n");
            if (f.sharedExitFrames > 0) {
                b.append("共同调用栈帧：").append(f.sharedExitFrames).append('\n');
            }
            if (f.input != null && !f.input.isBlank()) b.append("输入：").append(f.input).append('\n');
            if (f.result != null && !f.result.isBlank()) b.append("结果：").append(f.result).append('\n');
            if (f.exception != null && !f.exception.isBlank()) b.append("异常：").append(f.exception).append('\n');
            if (f.correlationScore > 0) b.append("退出关联：").append(f.correlationScore).append("/100\n");
            if (f.detail != null && !f.detail.equals(f.summary)) b.append("详情：").append(f.detail).append('\n');
            for (String e : f.evidence) b.append("证据：").append(e).append('\n');
            for (FixRecommendation recommendation : f.recommendations) {
                b.append("说明：").append(recommendation.title).append('\n');
                b.append("为什么检测：").append(recommendation.whyDetected).append('\n');
                b.append("开源项目说明：").append(recommendation.projectExplanation).append('\n');
                b.append("为什么归因：").append(recommendation.whyAttributed).append('\n');
                b.append("修复/排查：").append(recommendation.repair).append('\n');
                b.append("参考：").append(recommendation.source).append('\n');
            }
            b.append('\n');
        }
        return b.toString();
    }

    public String recommendationText() {
        StringBuilder b = new StringBuilder();
        if (!attribution.isBlank()) {
            b.append("归因结果：").append(attribution).append("\n\n");
        }
        int count = 0;
        for (DiagnosticFinding finding : findings) {
            for (FixRecommendation recommendation : finding.recommendations) {
                count++;
                b.append(count).append(". ").append(recommendation.title).append('\n');
                b.append("对应：")
                        .append(finding.attributionRank == 1 ? "主要归因 · " : "次要归因 · ")
                        .append(finding.title)
                        .append("（").append(finding.correlationScore).append("/100）\n");
                b.append("为什么检测：").append(recommendation.whyDetected).append('\n');
                b.append("开源项目说明：").append(recommendation.projectExplanation).append('\n');
                b.append("为什么这次归因：").append(recommendation.whyAttributed).append('\n');
                b.append("应该怎样修复/排查：").append(recommendation.repair).append('\n');
                b.append("参考：").append(recommendation.source).append("\n\n");
            }
        }
        if (count == 0) {
            return "当前归因证据不足，因此不生成修复建议。\n";
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
            o.put("sessionId", sessionId);
            o.put("observedEventCount", observedEventCount);
            o.put("lastExitTimestamp", lastExitTimestamp);
            o.put("exitPid", exitPid);
            o.put("exitTid", exitTid);
            o.put("exitThread", exitThread);
            o.put("exitSource", exitSource);
            o.put("exitStack", exitStack);
            o.put("exitRuleId", exitRuleId);
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
