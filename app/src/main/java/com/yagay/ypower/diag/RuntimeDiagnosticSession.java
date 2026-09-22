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
        AppProfile original = store.getProfile(packageName);

        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(key(packageName, "active"), true)
                .putLong(key(packageName, "start"), System.currentTimeMillis())
                .putString(key(packageName, "level"), level.name())
                .putString(key(packageName, "original"), original.toJson().toString())
                .apply();

        AppProfile tracing = AppProfile.fromJson(original.toJson().toString(), packageName);
        tracing.enabled = true;
        tracing.tracePackageScan = true;
        tracing.traceFiles = true;
        tracing.traceCommands = true;
        tracing.traceProperties = true;
        tracing.traceStacks = level != DiagnosticLevel.QUICK;
        tracing.traceJava = true;
        tracing.traceEnvironment = true;
        store.save(tracing);
        XposedBridgeManager.requestScope(packageName);

        if (RootShell.isRootAvailable()) {
            RootShell.exec("am force-stop " + ShellEscaper.q(packageName) + " || true");
        }

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

        String originalJson = prefs.getString(key(packageName, "original"), null);
        if (originalJson != null) {
            AppProfile original = AppProfile.fromJson(originalJson, packageName);
            ProfileStore.get(app).save(original);
            if (original.enabled) XposedBridgeManager.requestScope(packageName);
            else XposedBridgeManager.removeScope(packageName);
        }

        prefs.edit()
                .putBoolean(key(packageName, "active"), false)
                .putLong(key(packageName, "end"), state.endMs)
                .apply();

        return state;
    }

    public static SessionState state(Context context, String packageName) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SessionState s = new SessionState();
        s.active = prefs.getBoolean(key(packageName, "active"), false);
        s.startMs = prefs.getLong(key(packageName, "start"), 0L);
        s.endMs = prefs.getLong(key(packageName, "end"), 0L);
        try {
            s.level = DiagnosticLevel.valueOf(prefs.getString(key(packageName, "level"), DiagnosticLevel.STANDARD.name()));
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
    }
}
