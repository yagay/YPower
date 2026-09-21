package com.yagay.ypower.xposed;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.List;

import io.github.libxposed.service.XposedService;

public final class XposedBridgeManager {
    private static final String TAG = "YPowerXposed";
    private static volatile XposedService service;

    private XposedBridgeManager() {}

    public static void setService(XposedService value) { service = value; }
    public static boolean isReady() { return service != null; }

    public static SharedPreferences remotePreferences() {
        XposedService s = service;
        if (s == null) return null;
        try { return s.getRemotePreferences("ypower"); }
        catch (Throwable t) { Log.w(TAG, "Remote preferences unavailable", t); return null; }
    }

    public static void requestScope(String packageName) {
        XposedService s = service;
        if (s == null || packageName == null || packageName.isBlank()) return;
        try {
            s.requestScope(List.of(packageName), new XposedService.OnScopeEventListener() {
                @Override public void onScopeRequestApproved(List<String> approved) {
                    Log.i(TAG, "Scope approved: " + approved);
                }
                @Override public void onScopeRequestFailed(String message) {
                    Log.w(TAG, "Scope request failed: " + message);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "requestScope failed: " + packageName, t);
        }
    }

    public static void removeScope(String packageName) {
        XposedService s = service;
        if (s == null || packageName == null || packageName.isBlank()) return;
        try { s.removeScope(List.of(packageName)); }
        catch (Throwable t) { Log.w(TAG, "removeScope failed: " + packageName, t); }
    }
}
