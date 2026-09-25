package com.yagay.ypower.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.yagay.ypower.model.AppProfile;
import com.yagay.ypower.model.RecommendedAppPreset;
import com.yagay.ypower.xposed.XposedBridgeManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProfileStore {
    public static final String PREFS = "ypower_local";
    private static final String KEY_ENABLED = "enabledPackages";
    private static final String PROFILE_PREFIX = "profile:";
    private static volatile ProfileStore instance;
    private final SharedPreferences local;

    private ProfileStore(Context context) {
        local = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static ProfileStore get(Context context) {
        if (instance == null) {
            synchronized (ProfileStore.class) {
                if (instance == null) instance = new ProfileStore(context);
            }
        }
        return instance;
    }

    public synchronized AppProfile getProfile(String packageName) {
        return AppProfile.fromJson(local.getString(PROFILE_PREFIX + packageName, null), packageName);
    }

    public synchronized void save(AppProfile profile) {
        local.edit().putString(PROFILE_PREFIX + profile.packageName, profile.toJson().toString()).apply();
        List<String> enabled = new ArrayList<>(getEnabledPackages());
        if (profile.enabled && !enabled.contains(profile.packageName)) enabled.add(profile.packageName);
        if (!profile.enabled) enabled.remove(profile.packageName);
        writeEnabled(local, enabled);
        syncProfileToRemote(profile);
    }

    public synchronized void setEnabled(String packageName, boolean enabled) {
        AppProfile p = getProfile(packageName);
        p.enabled = enabled;
        save(p);
        if (enabled) XposedBridgeManager.requestScope(packageName);
        else XposedBridgeManager.removeScope(packageName);
    }

    public synchronized AppProfile applyRecommendedPreset(RecommendedAppPreset preset) {
        AppProfile p = getProfile(preset.packageName);
        p.enabled = true;

        p.dozeWhitelist = preset.dozeWhitelist;
        p.backgroundOps = preset.backgroundOps;
        p.standbyActive = preset.standbyActive;
        p.backgroundData = preset.backgroundData;

        p.simulateSystemApp = preset.simulateSystemApp;
        p.simulatePermissions = preset.simulatePermissions;

        p.tracePackageScan = preset.tracePackageScan;
        p.traceFiles = preset.traceFiles;
        p.traceCommands = preset.traceCommands;
        p.traceProperties = preset.traceProperties;
        p.tracePermissions = preset.tracePermissions;
        p.traceDebugger = preset.traceDebugger;
        p.traceExceptions = preset.traceExceptions;
        p.traceStacks = preset.traceStacks;
        p.traceJava = p.anyTraceEnabled();
        p.traceEnvironment = p.anyTraceEnabled();

        save(p);
        XposedBridgeManager.requestScope(p.packageName);
        return p;
    }

    public synchronized List<String> getEnabledPackages() {
        return readEnabled(local);
    }

    public synchronized void syncAllToRemote() {
        SharedPreferences remote = XposedBridgeManager.remotePreferences();
        if (remote == null) return;
        List<String> enabled = getEnabledPackages();
        SharedPreferences.Editor e = remote.edit();
        e.putString(KEY_ENABLED, toJson(enabled));
        for (String pkg : enabled) {
            e.putString(PROFILE_PREFIX + pkg, local.getString(PROFILE_PREFIX + pkg, new AppProfile(pkg).toJson().toString()));
        }
        e.apply();
    }

    private void syncProfileToRemote(AppProfile profile) {
        SharedPreferences remote = XposedBridgeManager.remotePreferences();
        if (remote == null) return;
        SharedPreferences.Editor e = remote.edit();
        e.putString(PROFILE_PREFIX + profile.packageName, profile.toJson().toString());
        e.putString(KEY_ENABLED, toJson(getEnabledPackages()));
        e.apply();
    }

    private static List<String> readEnabled(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_ENABLED, "[]");
        if (raw == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                String v = a.optString(i, "");
                if (!v.isEmpty()) out.add(v);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static void writeEnabled(SharedPreferences prefs, List<String> values) {
        prefs.edit().putString(KEY_ENABLED, toJson(values)).apply();
    }

    private static String toJson(List<String> values) {
        JSONArray a = new JSONArray();
        for (String value : values) a.put(value);
        return a.toString();
    }
}
