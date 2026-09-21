package com.yagay.ypower.diag;

import java.util.List;

public final class DetectionRules {
    private DetectionRules() {}

    public static final List<String> ROOT_PACKAGES = List.of(
            "com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch",
            "com.kingroot.kinguser", "eu.chainfire.supersu");
    public static final List<String> HOOK_PACKAGES = List.of(
            "org.lsposed.manager", "de.robv.android.xposed.installer");
    public static final List<String> VIRTUAL_PACKAGES = List.of(
            "io.va.exposed", "com.vmos.pro", "com.vmos.app", "com.lbe.parallel.intl",
            "com.parallel.space.lite", "com.excelliance.multiaccounts");
    public static final List<String> ROOT_PATHS = List.of(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/data/adb/magisk", "/data/adb/ksu", "/data/adb/ap", "/data/adb/modules");
    public static final List<String> MAP_KEYWORDS = List.of(
            "lsposed", "xposed", "zygisk", "riru", "frida", "gum-js-loop",
            "shadowhook", "bytehook", "libxposed");
    public static final List<String> CRASH_KEYWORDS = List.of(
            "FATAL EXCEPTION", "Fatal signal", "SIGABRT", "SIGSEGV", "SIGBUS",
            "ANR in", "SecurityException", "UnsatisfiedLinkError", "dlopen failed",
            "OutOfMemoryError", "DeadObjectException", "avc: denied", "lmkd",
            "chromium", "WebView", "SQLiteException", "SSLHandshakeException");
}
