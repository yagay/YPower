package com.yagay.ypower.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AppProfile {
    public String packageName;
    public boolean enabled;
    public boolean dozeWhitelist = true;
    public boolean backgroundOps = true;
    public boolean standbyActive = true;
    public boolean backgroundData = true;
    public boolean autoGrantDangerous;
    public boolean simulateSystemApp;
    public boolean simulatePermissions;
    public boolean traceJava;
    public boolean traceEnvironment;
    public final List<String> simulatedPermissions = new ArrayList<>();

    public AppProfile(String packageName) {
        this.packageName = packageName;
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
            o.put("traceJava", traceJava);
            o.put("traceEnvironment", traceEnvironment);
            JSONArray a = new JSONArray();
            for (String p : simulatedPermissions) a.put(p);
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
            p.traceJava = o.optBoolean("traceJava", false);
            p.traceEnvironment = o.optBoolean("traceEnvironment", false);
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
