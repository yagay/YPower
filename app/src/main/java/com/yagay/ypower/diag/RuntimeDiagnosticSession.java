package com.yagay.ypower.diag;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.model.AppProfile;
import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.util.ShellEscaper;
import com.yagay.ypower.xposed.XposedBridgeManager;

public final class RuntimeDiagnosticSession {
    private static final String PREFS = "ypower_runtime_diag";

    private RuntimeDiagnosticSession() {}

    public static SessionState start(Context context, String packageName, DiagnosticLevel level) {
        Context app = context.getApplicationContext();
        ProfileStore store = ProfileStore.get(app);
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        SessionState existing = state(app, packageName);
        if (existing.active) return existing;

        AppProfile original = store.getProfile(packageName);

        String sessionId = packageName + "-" + Long.toHexString(System.currentTimeMillis());

        AppProfile tracing = AppProfile.fromJson(original.toJson().toString(), packageName);
        tracing.enabled = true;
        tracing.diagnosticSessionId = sessionId;
        tracing.tracePackageScan = true;
        tracing.traceFiles = true;
        tracing.traceCommands = true;
        tracing.traceProperties = true;
        tracing.traceStacks = level != DiagnosticLevel.QUICK;
        tracing.traceJava = true;
        tracing.traceEnvironment = true;
        store.save(tracing);
        XposedBridgeManager.requestScope(packageName);

        // Stop the old target process before the measured window begins.
        // This ensures YPower's own force-stop is never reported as an app runtime exit.
        if (RootShell.isRootAvailable()) {
            RootShell.exec("am force-stop " + ShellEscaper.q(packageName) + " || true");
        }

        long startMs = System.currentTimeMillis();
        prefs.edit()
                .putBoolean(key(packageName, "active"), true)
                .putLong(key(packageName, "start"), startMs)
                .putLong(key(packageName, "end"), 0L)
                .putString(key(packageName, "level"), level.name())
                .putString(key(packageName, "sessionId"), sessionId)
                .putString(key(packageName, "original"), original.toJson().toString())
                .apply();

        return state(app, packageName);
    }

    public static boolean launchTarget(Context context, String packageName) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
        return true;
    }

    public static SessionState finishAndRestore(Context context, String packageName) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SessionState state = state(app, packageName);
        state.endMs = System.currentTimeMillis();

        // Close the measured window before YPower stops the process to unload temporary hooks.
        prefs.edit()
                .putBoolean(key(packageName, "active"), false)
                .putLong(key(packageName, "end"), state.endMs)
                .apply();

        if (RootShell.isRootAvailable()) {
            RootShell.exec("am force-stop " + ShellEscaper.q(packageName) + " || true");
        }

        String originalJson = prefs.getString(key(packageName, "original"), null);
        if (originalJson != null) {
            AppProfile original = AppProfile.fromJson(originalJson, packageName);
            ProfileStore.get(app).save(original);
            if (original.enabled) XposedBridgeManager.requestScope(packageName);
            else XposedBridgeManager.removeScope(packageName);
        }

        return state;
    }

    public static SessionState state(Context context, String packageName) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SessionState s = new SessionState();
        s.active = prefs.getBoolean(key(packageName, "active"), false);
        s.startMs = prefs.getLong(key(packageName, "start"), 0L);
        s.endMs = prefs.getLong(key(packageName, "end"), 0L);
        s.sessionId = prefs.getString(key(packageName, "sessionId"), "");
        try {
            s.level = DiagnosticLevel.valueOf(
                    prefs.getString(key(packageName, "level"), DiagnosticLevel.STANDARD.name())
            );
        } catch (Exception ignored) {
            s.level = DiagnosticLevel.STANDARD;
        }
        return s;
    }

    private static String key(String packageName, String suffix) {
        return packageName + ":" + suffix;
    }

    public static final class SessionState {
        public boolean active;
        public long startMs;
        public long endMs;
        public DiagnosticLevel level;
        public String sessionId;
    }
}
