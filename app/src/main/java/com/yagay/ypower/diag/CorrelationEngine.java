package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CorrelationEngine {
    private CorrelationEngine() {}

    public static void analyze(DiagnosticReport report) {
        for (DiagnosticFinding finding : report.findings) {
            finding.attributionRank = 0;
            finding.sameThreadAsExit = false;
            finding.sharedExitFrames = 0;

            if (!"exit".equals(finding.category)) {
                finding.correlationScore = score(report, finding);
            }
        }

        if (report.findings.isEmpty()) {
            report.exitSummary = "";
            report.attribution = "";
            return;
        }

        if (report.lastExitTimestamp <= 0) {
            report.exitSummary = "";
            report.attribution =
                    "本次运行观察到检测行为，但没有记录到真实退出，因此不做原因归因。";
            return;
        }

        report.exitSummary = "本次运行记录到真实退出/崩溃事件";

        List<DiagnosticFinding> candidates = new ArrayList<>();
        for (DiagnosticFinding finding : report.findings) {
            if (!"exit".equals(finding.category) && finding.correlationScore > 0) {
                candidates.add(finding);
            }
        }

        candidates.sort(
                Comparator.comparingInt((DiagnosticFinding f) -> f.correlationScore)
                        .reversed()
        );

        if (candidates.isEmpty() || candidates.get(0).correlationScore < 45) {
            report.attribution =
                    "记录到了退出，但当前证据不足以确认是哪一项检测触发。";
            return;
        }

        DiagnosticFinding primary = candidates.get(0);
        primary.attributionRank = 1;

        DiagnosticFinding secondary = null;
        if (candidates.size() > 1) {
            DiagnosticFinding second = candidates.get(1);
            int gap = primary.correlationScore - second.correlationScore;

            if (second.correlationScore >= 60 && gap <= 15) {
                second.attributionRank = 2;
                secondary = second;
            }
        }

        StringBuilder attribution = new StringBuilder();
        attribution.append("主要归因：")
                .append(primary.title)
                .append("（")
                .append(primary.correlationScore)
                .append("/100，")
                .append(strength(primary.correlationScore))
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

    private static int score(DiagnosticReport report, DiagnosticFinding finding) {
        int score = 0;

        // 1) Temporal proximity: max 35.
        if (finding.closestDeltaMs != Long.MAX_VALUE) {
            long delta = finding.closestDeltaMs;
            if (delta <= 100) score += 35;
            else if (delta <= 500) score += 30;
            else if (delta <= 1500) score += 22;
            else if (delta <= 5000) score += 14;
            else if (delta <= 15000) score += 6;
        }

        // 2) The check actually returned a suspicious/positive result: max 20.
        if (finding.matchedCount > 0) {
            score += 20;
        } else if (finding.totalCount > 0) {
            // The application definitely performed the check, but YPower did not observe a
            // positive result. Keep it as weak evidence rather than treating it as a hit.
            score += 4;
        }

        // 3) Same PID/TID as the exact Java-side exit: max 15.
        if (report.exitPid >= 0 && finding.pid == report.exitPid) {
            score += 5;
        }

        if (report.exitTid >= 0 && finding.tid == report.exitTid) {
            finding.sameThreadAsExit = true;
            score += 10;
        }

        // 4) Shared business call-stack frames: max 20.
        int shared = sharedBusinessFrames(finding.stack, report.exitStack);
        finding.sharedExitFrames = shared;
        score += Math.min(20, shared * 5);

        // 5) Repetition inside the same measured session: max 10.
        if (finding.matchedCount >= 3) score += 10;
        else if (finding.matchedCount >= 2) score += 7;
        else if (finding.totalCount >= 3) score += 4;

        // 6) An actual exception from the observed API call is additional evidence.
        if (finding.exception != null && !finding.exception.isBlank()) {
            score += 8;
        }

        return Math.min(100, score);
    }

    private static int sharedBusinessFrames(String detectionStack, String exitStack) {
        if (detectionStack == null || detectionStack.isBlank()
                || exitStack == null || exitStack.isBlank()) {
            return 0;
        }

        Set<String> detection = new HashSet<>();
        for (String frame : detectionStack.split(" <- ")) {
            String normalized = normalizeFrame(frame);
            if (!normalized.isBlank() && !isFrameworkFrame(normalized)) {
                detection.add(normalized);
            }
        }

        int shared = 0;
        Set<String> counted = new HashSet<>();

        for (String frame : exitStack.split(" <- ")) {
            String normalized = normalizeFrame(frame);
            if (normalized.isBlank() || isFrameworkFrame(normalized)) continue;

            if (detection.contains(normalized) && counted.add(normalized)) {
                shared++;
            }
        }

        return shared;
    }

    private static String normalizeFrame(String frame) {
        if (frame == null) return "";
        String value = frame.trim();
        int colon = value.lastIndexOf(':');
        if (colon > value.lastIndexOf('.')) {
            value = value.substring(0, colon);
        }
        return value;
    }

    private static boolean isFrameworkFrame(String frame) {
        return frame.startsWith("java.")
                || frame.startsWith("javax.")
                || frame.startsWith("android.")
                || frame.startsWith("androidx.")
                || frame.startsWith("kotlin.")
                || frame.startsWith("dalvik.")
                || frame.startsWith("libcore.");
    }

    private static String strength(int score) {
        if (score >= 85) return "高可信";
        if (score >= 70) return "较强相关";
        if (score >= 55) return "中等相关";
        return "可能相关";
    }
}
