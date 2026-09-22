package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CorrelationEngine {
    private CorrelationEngine() {}

    public static void analyze(DiagnosticReport report) {
        if (report.findings.isEmpty()) {
            report.exitSummary = "";
            report.attribution = "";
            return;
        }

        List<DiagnosticFinding> ranked = new ArrayList<>(report.findings);
        ranked.sort(Comparator.comparingInt((DiagnosticFinding f) -> f.correlationScore).reversed());

        DiagnosticFinding top = ranked.get(0);

        if (report.lastExitTimestamp <= 0) {
            report.exitSummary = "";
            report.attribution = "本次运行观察到检测行为，但没有记录到异常退出。";
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

        if (top.correlationScore >= 80 && !"exit".equals(top.category)) {
            report.attribution = top.title + " 与退出时间高度接近（" + top.correlationScore + "/100）";
        } else if (top.correlationScore >= 50 && !"exit".equals(top.category)) {
            report.attribution = top.title + " 与退出存在时间关联（" + top.correlationScore + "/100）";
        } else {
            DiagnosticFinding bestNonExit = null;
            for (DiagnosticFinding finding : ranked) {
                if (!"exit".equals(finding.category)) {
                    bestNonExit = finding;
                    break;
                }
            }
            if (bestNonExit != null && bestNonExit.correlationScore >= 40) {
                report.attribution = bestNonExit.title + " 是退出前最近的可识别检测之一（"
                        + bestNonExit.correlationScore + "/100）";
            } else {
                report.attribution = "记录到了退出，但当前运行证据不足以确认是哪项检测触发。";
            }
        }
    }
}
