package com.yagay.ypower.model;

public enum DiagnosticStatus {
    DETECTED("检测到", "DETECTED"),
    PASS("通过", "PASS"),
    FAIL("异常", "FAIL"),
    WARN("警告", "WARN"),
    UNKNOWN("未知", "UNKNOWN");

    public final String zh;
    public final String code;

    DiagnosticStatus(String zh, String code) {
        this.zh = zh;
        this.code = code;
    }
}
