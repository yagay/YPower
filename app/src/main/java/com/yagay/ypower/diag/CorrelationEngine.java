package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CorrelationEngine {
    private CorrelationEngine() {}

    public static void analyze(DiagnosticReport report) {
        for (DiagnosticFinding finding : report.findings) {
            finding.attributionRank = 0;
        }

        if (report.findings.isEmpty()) {
            report.exitSummary = "";
            report.attribution = "";
            return;
        }

        if (report.lastExitTimestamp <= 0) {
            report.exitSummary = "";
            report.attribution = "本次运行观察到检测行为，但没有记录到异常退出，因此不做原因归因。";
            return;
        }

        boolean abnormalExit = false;
        for (DiagnosticFinding finding : report.findings) {
            if ("exit".equals(finding.category) && finding.correlationScore >= 90) {
                abnormalExit = true;
                break;
            }
        }
        if (abnormalExit) {
            report.exitSummary = "本次运行记录到真实退出/崩溃事件";
        }

        List<DiagnosticFinding> candidates = new ArrayList<>();
        for (DiagnosticFinding finding : report.findings) {
            if (!"exit".equals(finding.category) && finding.correlationScore > 0) {
                candidates.add(finding);
            }
        }
        candidates.sort(Comparator.comparingInt((DiagnosticFinding f) -> f.correlationScore).reversed());

        if (candidates.isEmpty() || candidates.get(0).correlationScore < 40) {
            report.attribution = "记录到了退出，但当前运行证据不足以确认是哪项检测触发。";
            return;
        }

        DiagnosticFinding primary = candidates.get(0);
        primary.attributionRank = 1;

        DiagnosticFinding secondary = null;
        if (candidates.size() > 1) {
            DiagnosticFinding second = candidates.get(1);
            int gap = primary.correlationScore - second.correlationScore;
            if (second.correlationScore >= 55 && gap <= 20) {
                second.attributionRank = 2;
                secondary = second;
            }
        }

        String strength;
        if (primary.correlationScore >= 80) {
            strength = "高度相关";
        } else if (primary.correlationScore >= 60) {
            strength = "较强相关";
        } else {
            strength = "可能相关";
        }

        StringBuilder attribution = new StringBuilder();
        attribution.append("主要归因：")
                .append(primary.title)
                .append("（")
                .append(primary.correlationScore)
                .append("/100，")
                .append(strength)
                .append("）");

        if (secondary != null) {
            attribution.append("；次要归因：")
                    .append(secondary.title)
                    .append("（")
                    .append(secondary.correlationScore)
                    .append("/100）");
        }

        report.attribution = attribution.toString();
    }
}
