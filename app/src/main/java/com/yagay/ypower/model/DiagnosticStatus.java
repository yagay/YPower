package com.yagay.ypower.model;

public enum DiagnosticStatus {
    PASS("通过", "PASS"),
    FAIL("未通过", "FAIL"),
    WARN("警告", "WARN"),
    UNKNOWN("未知", "UNKNOWN");

    public final String zh;
    public final String code;

    DiagnosticStatus(String zh, String code) {
        this.zh = zh;
        this.code = code;
    }
}
