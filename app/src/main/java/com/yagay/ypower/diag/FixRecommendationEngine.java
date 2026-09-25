package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.FixRecommendation;

public final class FixRecommendationEngine {
    private FixRecommendationEngine() {}

    public static void apply(DiagnosticReport report) {
        for (DiagnosticFinding finding : report.findings) {
            finding.recommendations.clear();
        }

        if (report.lastExitTimestamp <= 0) return;

        for (DiagnosticFinding finding : report.findings) {
            if (finding.attributionRank != 1 && finding.attributionRank != 2) continue;

            DetectionRuleDefinition rule = DetectionRuleCatalog.getOrDefault(
                    finding.ruleId,
                    finding.category,
                    finding.title
            );

            String role = finding.attributionRank == 1 ? "主要归因" : "次要归因";
            finding.recommendations.add(new FixRecommendation(
                    role + "：" + rule.title,
                    rule.whyDetected,
                    rule.projectExplanation,
                    buildAttributionExplanation(finding, role),
                    rule.remediation,
                    rule.reference
            ));
        }
    }

    private static String buildAttributionExplanation(DiagnosticFinding f, String role) {
        StringBuilder b = new StringBuilder();

        b.append("本项被标记为").append(role)
                .append("，规则 ID=").append(f.ruleId)
                .append("，代表状态=").append(f.representativeState)
                .append("，关联分数=").append(f.correlationScore).append("/100。");

        if (f.closestDeltaMs != Long.MAX_VALUE) {
            b.append(" 离真实退出最近的一次同规则事件相隔 ")
                    .append(f.closestDeltaMs)
                    .append(" ms。");
        }

        b.append(" 本次共记录 ")
                .append(f.totalCount)
                .append(" 次：HIT ").append(f.hitCount)
                .append("、CHECKED ").append(f.checkedCount)
                .append("、NOT_HIT ").append(f.notHitCount)
                .append("、UNKNOWN ").append(f.unknownCount)
                .append("。");

        if (f.sameThreadAsExit) {
            b.append(" 代表事件与退出发生在同一线程。");
        }

        if (f.sharedExitFrames > 0) {
            b.append(" 检测栈与退出栈有 ")
                    .append(f.sharedExitFrames)
                    .append(" 个共同业务调用帧。");
        }

        if (f.input != null && !f.input.isBlank()) {
            b.append(" 代表输入：").append(f.input).append("。");
        }

        if (f.result != null && !f.result.isBlank()) {
            b.append(" 代表返回结果：").append(f.result).append("。");
        }

        if (f.exception != null && !f.exception.isBlank()) {
            b.append(" 代表调用异常：").append(f.exception).append("。");
        }

        if (f.representativeState.name().equals("CHECKED")) {
            b.append(" 注意：CHECKED 只证明应用执行了检查，并不证明检查结果命中了风险状态。");
        }

        return b.toString();
    }
}
