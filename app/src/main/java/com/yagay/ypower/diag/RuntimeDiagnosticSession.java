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
        tracing.tracePermissions = true;
        tracing.traceDebugger = true;
        tracing.traceNative = level == DiagnosticLevel.DEEP;
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

        SystemTraceCollector.CaptureState capture =
                SystemTraceCollector.start(app, packageName, sessionId, level);

        long startMs = System.currentTimeMillis();
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(key(packageName, "active"), true)
                .putLong(key(packageName, "start"), startMs)
                .putLong(key(packageName, "end"), 0L)
                .putString(key(packageName, "level"), level.name())
                .putString(key(packageName, "sessionId"), sessionId)
                .putString(key(packageName, "original"), original.toJson().toString());
        writeCaptureState(editor, packageName, capture);
        editor.apply();

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

        state.systemTrace = SystemTraceCollector.stop(app, state.systemTrace);

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
        s.systemTrace = readCaptureState(prefs, packageName, s.sessionId);
        try {
            s.level = DiagnosticLevel.valueOf(
                    prefs.getString(key(packageName, "level"), DiagnosticLevel.STANDARD.name())
            );
        } catch (Exception ignored) {
            s.level = DiagnosticLevel.STANDARD;
        }
        return s;
    }

    private static void writeCaptureState(
            SharedPreferences.Editor editor,
            String packageName,
            SystemTraceCollector.CaptureState capture
    ) {
        if (capture == null) return;
        editor.putBoolean(key(packageName, "perfettoAvailable"), capture.perfettoAvailable);
        editor.putBoolean(key(packageName, "perfettoStarted"), capture.perfettoStarted);
        editor.putString(key(packageName, "perfettoKey"), capture.perfettoKey);
        editor.putString(key(packageName, "perfettoConfigPath"), capture.perfettoConfigPath);
        editor.putString(key(packageName, "perfettoTempPath"), capture.perfettoTempPath);
        editor.putBoolean(key(packageName, "simpleperfAvailable"), capture.simpleperfAvailable);
        editor.putBoolean(key(packageName, "simpleperfStarted"), capture.simpleperfStarted);
        editor.putInt(key(packageName, "simpleperfPid"), capture.simpleperfPid);
        editor.putString(key(packageName, "simpleperfTempPath"), capture.simpleperfTempPath);
        editor.putString(key(packageName, "simpleperfLogPath"), capture.simpleperfLogPath);
        editor.putString(key(packageName, "traceTempDir"), capture.tempDir);
    }

    private static SystemTraceCollector.CaptureState readCaptureState(
            SharedPreferences prefs,
            String packageName,
            String sessionId
    ) {
        SystemTraceCollector.CaptureState capture = new SystemTraceCollector.CaptureState();
        capture.sessionId = sessionId == null ? "" : sessionId;
        capture.perfettoAvailable = prefs.getBoolean(key(packageName, "perfettoAvailable"), false);
        capture.perfettoStarted = prefs.getBoolean(key(packageName, "perfettoStarted"), false);
        capture.perfettoKey = prefs.getString(key(packageName, "perfettoKey"), "");
        capture.perfettoConfigPath = prefs.getString(key(packageName, "perfettoConfigPath"), "");
        capture.perfettoTempPath = prefs.getString(key(packageName, "perfettoTempPath"), "");
        capture.simpleperfAvailable = prefs.getBoolean(key(packageName, "simpleperfAvailable"), false);
        capture.simpleperfStarted = prefs.getBoolean(key(packageName, "simpleperfStarted"), false);
        capture.simpleperfPid = prefs.getInt(key(packageName, "simpleperfPid"), -1);
        capture.simpleperfTempPath = prefs.getString(key(packageName, "simpleperfTempPath"), "");
        capture.simpleperfLogPath = prefs.getString(key(packageName, "simpleperfLogPath"), "");
        capture.tempDir = prefs.getString(key(packageName, "traceTempDir"), "");
        return capture;
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
        public SystemTraceCollector.CaptureState systemTrace = new SystemTraceCollector.CaptureState();
    }
}
