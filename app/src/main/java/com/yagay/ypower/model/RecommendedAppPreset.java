package com.yagay.ypower.model;

public final class RecommendedAppPreset {
    public final String packageName;
    public final String displayName;
    public final String reason;

    public final boolean dozeWhitelist;
    public final boolean backgroundOps;
    public final boolean standbyActive;
    public final boolean backgroundData;

    public final boolean simulateSystemApp;
    public final boolean simulatePermissions;

    public final boolean tracePackageScan;
    public final boolean traceFiles;
    public final boolean traceCommands;
    public final boolean traceProperties;
    public final boolean traceStacks;

    public RecommendedAppPreset(
            String packageName,
            String displayName,
            String reason,
            boolean dozeWhitelist,
            boolean backgroundOps,
            boolean standbyActive,
            boolean backgroundData,
            boolean simulateSystemApp,
            boolean simulatePermissions,
            boolean tracePackageScan,
            boolean traceFiles,
            boolean traceCommands,
            boolean traceProperties,
            boolean traceStacks
    ) {
        this.packageName = packageName;
        this.displayName = displayName;
        this.reason = reason;
        this.dozeWhitelist = dozeWhitelist;
        this.backgroundOps = backgroundOps;
        this.standbyActive = standbyActive;
        this.backgroundData = backgroundData;
        this.simulateSystemApp = simulateSystemApp;
        this.simulatePermissions = simulatePermissions;
        this.tracePackageScan = tracePackageScan;
        this.traceFiles = traceFiles;
        this.traceCommands = traceCommands;
        this.traceProperties = traceProperties;
        this.traceStacks = traceStacks;
    }

    public String hookSummary() {
        StringBuilder b = new StringBuilder();
        if (tracePackageScan) append(b, "包扫描");
        if (traceFiles) append(b, "文件/proc");
        if (traceCommands) append(b, "命令/退出");
        if (traceProperties) append(b, "系统属性");
        if (simulateSystemApp) append(b, "系统身份模拟");
        if (simulatePermissions) append(b, "权限状态模拟");
        if (traceStacks) append(b, "短调用栈");
        return b.length() == 0 ? "无需 LSPosed Hook" : b.toString();
    }

    private static void append(StringBuilder b, String text) {
        if (b.length() > 0) b.append("、");
        b.append(text);
    }
}
