package com.yagay.ypower.diag;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.DiagnosticStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CorrelationEngine {
    private CorrelationEngine() {}

    public static void analyze(DiagnosticReport report) {
        String raw = String.join("\n", report.raw).toLowerCase(Locale.ROOT);
        boolean abort = raw.contains("sigabrt") || raw.contains("fatal signal 6");
        boolean segv = raw.contains("sigsegv") || raw.contains("fatal signal 11");
        boolean javaCrash = raw.contains("fatal exception") || raw.contains("androidruntime");
        boolean anr = raw.contains("anr in") || raw.contains("input dispatching timed out");
        boolean lmk = raw.contains("lmkd") || raw.contains("lowmemorykiller");

        if (abort) report.exitSummary = "检测到 SIGABRT / 主动中止迹象";
        else if (segv) report.exitSummary = "检测到 SIGSEGV / Native 非法访问";
        else if (javaCrash) report.exitSummary = "检测到 Java/Kotlin FATAL EXCEPTION";
        else if (anr) report.exitSummary = "检测到 ANR";
        else if (lmk) report.exitSummary = "检测到内存压力结束进程的迹象";

        for (DiagnosticFinding f : report.findings) {
            int score = 0;
            if (f.status == DiagnosticStatus.FAIL) score += 18;
            if (f.status == DiagnosticStatus.WARN) score += 6;
            String key = (f.category + " " + f.title + " " + f.summary).toLowerCase(Locale.ROOT);
            if (abort && containsAny(key, "root", "hook", "mount", "selinux", "integrity")) score += 22;
            if (segv && containsAny(key, "native", "elf", "library", "memory")) score += 25;
            if (javaCrash && containsAny(key, "permission", "binder", "java", "webview")) score += 18;
            if (raw.contains("ypowertrace") && containsAny(raw, f.category.toLowerCase(Locale.ROOT), f.title.toLowerCase(Locale.ROOT))) score += 30;
            f.correlationScore = Math.min(100, score);
        }

        List<DiagnosticFinding> ranked = new ArrayList<>(report.findings);
        ranked.sort(Comparator.comparingInt((DiagnosticFinding f) -> f.correlationScore).reversed());
        if (!ranked.isEmpty() && ranked.get(0).correlationScore >= 45) {
            DiagnosticFinding top = ranked.get(0);
            report.attribution = top.title + " 与最近异常退出高度相关（" + top.correlationScore + "/100）";
        } else if (!ranked.isEmpty() && ranked.get(0).correlationScore >= 25) {
            DiagnosticFinding top = ranked.get(0);
            report.attribution = top.title + " 与异常退出存在关联（" + top.correlationScore + "/100），需要更多证据确认";
        }
    }

    private static boolean containsAny(String s, String... values) {
        for (String value : values) if (s.contains(value)) return true;
        return false;
    }
}
