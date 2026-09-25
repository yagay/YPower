package com.yagay.ypower.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AppProfile {
    public String packageName;
    public boolean enabled;

    // Root-side enhancements
    public boolean dozeWhitelist = true;
    public boolean backgroundOps = true;
    public boolean standbyActive = true;
    public boolean backgroundData = true;
    public boolean autoGrantDangerous;

    // LSPosed compatibility
    public boolean simulateSystemApp;
    public boolean simulatePermissions;

    // LSPosed diagnostics
    public boolean tracePackageScan;
    public boolean traceFiles;
    public boolean traceCommands;
    public boolean traceProperties;
    public boolean tracePermissions;
    public boolean traceDebugger;
    public boolean traceExceptions;
    public boolean traceNative;
    public boolean traceStacks = true;
    public String diagnosticSessionId = "";

    // Legacy fields kept for profile migration.
    public boolean traceJava;
    public boolean traceEnvironment;

    public final List<String> simulatedPermissions = new ArrayList<>();

    public AppProfile(String packageName) {
        this.packageName = packageName;
    }

    public boolean anyTraceEnabled() {
        return tracePackageScan || traceFiles || traceCommands || traceProperties
                || tracePermissions || traceDebugger || traceExceptions || traceNative;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("packageName", packageName);
            o.put("enabled", enabled);
            o.put("dozeWhitelist", dozeWhitelist);
            o.put("backgroundOps", backgroundOps);
            o.put("standbyActive", standbyActive);
            o.put("backgroundData", backgroundData);
            o.put("autoGrantDangerous", autoGrantDangerous);
            o.put("simulateSystemApp", simulateSystemApp);
            o.put("simulatePermissions", simulatePermissions);
            o.put("tracePackageScan", tracePackageScan);
            o.put("traceFiles", traceFiles);
            o.put("traceCommands", traceCommands);
            o.put("traceProperties", traceProperties);
            o.put("tracePermissions", tracePermissions);
            o.put("traceDebugger", traceDebugger);
            o.put("traceExceptions", traceExceptions);
            o.put("traceNative", traceNative);
            o.put("traceStacks", traceStacks);
            o.put("diagnosticSessionId", diagnosticSessionId);

            // Write legacy aggregate flags for downgrade compatibility.
            o.put("traceJava", anyTraceEnabled());
            o.put("traceEnvironment", anyTraceEnabled());

            JSONArray a = new JSONArray();
            for (String permission : simulatedPermissions) a.put(permission);
            o.put("simulatedPermissions", a);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static AppProfile fromJson(String json, String fallbackPackage) {
        AppProfile p = new AppProfile(fallbackPackage);
        if (json == null || json.isEmpty()) return p;
        try {
            JSONObject o = new JSONObject(json);
            p.packageName = o.optString("packageName", fallbackPackage);
            p.enabled = o.optBoolean("enabled", false);
            p.dozeWhitelist = o.optBoolean("dozeWhitelist", true);
            p.backgroundOps = o.optBoolean("backgroundOps", true);
            p.standbyActive = o.optBoolean("standbyActive", true);
            p.backgroundData = o.optBoolean("backgroundData", true);
            p.autoGrantDangerous = o.optBoolean("autoGrantDangerous", false);
            p.simulateSystemApp = o.optBoolean("simulateSystemApp", false);
            p.simulatePermissions = o.optBoolean("simulatePermissions", false);

            boolean legacyTrace = o.optBoolean("traceJava", false) || o.optBoolean("traceEnvironment", false);
            p.tracePackageScan = o.has("tracePackageScan") ? o.optBoolean("tracePackageScan", false) : legacyTrace;
            p.traceFiles = o.has("traceFiles") ? o.optBoolean("traceFiles", false) : legacyTrace;
            p.traceCommands = o.has("traceCommands") ? o.optBoolean("traceCommands", false) : legacyTrace;
            p.traceProperties = o.has("traceProperties") ? o.optBoolean("traceProperties", false) : legacyTrace;
            p.tracePermissions = o.optBoolean("tracePermissions", false);
            p.traceDebugger = o.optBoolean("traceDebugger", false);
            p.traceExceptions = o.optBoolean("traceExceptions", false);
            p.traceNative = o.optBoolean("traceNative", false);
            p.traceStacks = o.optBoolean("traceStacks", true);
            p.diagnosticSessionId = o.optString("diagnosticSessionId", "");
            p.traceJava = legacyTrace;
            p.traceEnvironment = legacyTrace;

            JSONArray a = o.optJSONArray("simulatedPermissions");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    String value = a.optString(i, "");
                    if (!value.isEmpty()) p.simulatedPermissions.add(value);
                }
            }
        } catch (JSONException ignored) {
        }
        return p;
    }
}
