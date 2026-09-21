package com.yagay.ypower.hook;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.yagay.ypower.model.AppProfile;

import org.json.JSONArray;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class YPowerModule extends XposedModule {
    private static final String TAG = "YPowerTrace";
    private static final String GROUP = "ypower";

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;
        String pkg = param.getPackageName();
        if (pkg == null || pkg.equals("com.yagay.ypower")) return;

        AppProfile profile = loadProfile(pkg);
        if (!profile.enabled) return;

        log(Log.INFO, TAG, "enabled package=" + pkg + " process=" + param.getProcessName());
        if (profile.simulateSystemApp) installSystemIdentityHooks(pkg);
        if (profile.simulatePermissions) installPermissionStatusHook(profile);
        if (profile.traceJava || profile.traceEnvironment) installEnvironmentTraceHooks();
    }

    private AppProfile loadProfile(String packageName) {
        try {
            SharedPreferences prefs = getRemotePreferences(GROUP);
            String enabledRaw = prefs.getString("enabledPackages", "[]");
            boolean enabled = false;
            JSONArray arr = new JSONArray(enabledRaw == null ? "[]" : enabledRaw);
            for (int i = 0; i < arr.length(); i++) {
                if (packageName.equals(arr.optString(i))) {
                    enabled = true;
                    break;
                }
            }
            AppProfile p = AppProfile.fromJson(prefs.getString("profile:" + packageName, null), packageName);
            p.enabled = enabled && p.enabled;
            return p;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "loadProfile failed: " + t);
            return new AppProfile(packageName);
        }
    }

    private void installSystemIdentityHooks(String packageName) {
        try {
            Method isSystem = ApplicationInfo.class.getDeclaredMethod("isSystemApp");
            hook(isSystem).intercept(chain -> {
                ApplicationInfo info = (ApplicationInfo) chain.getThisObject();
                if (info != null && packageName.equals(info.packageName)) return true;
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "isSystemApp hook failed: " + t);
        }
        try {
            Method isUpdated = ApplicationInfo.class.getDeclaredMethod("isUpdatedSystemApp");
            hook(isUpdated).intercept(chain -> {
                ApplicationInfo info = (ApplicationInfo) chain.getThisObject();
                if (info != null && packageName.equals(info.packageName)) return true;
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "isUpdatedSystemApp hook failed: " + t);
        }
    }

    private void installPermissionStatusHook(AppProfile profile) {
        try {
            Method method = Context.class.getDeclaredMethod("checkSelfPermission", String.class);
            hook(method).intercept(chain -> {
                Object arg = chain.getArg(0);
                String permission = arg instanceof String ? (String) arg : "";
                if (shouldSimulatePermission(profile, permission)) {
                    trace("permission", permission);
                    return PackageManager.PERMISSION_GRANTED;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "permission status hook failed: " + t);
        }
    }

    private boolean shouldSimulatePermission(AppProfile profile, String permission) {
        if (!profile.simulatePermissions || permission == null) return false;
        if (!profile.simulatedPermissions.isEmpty()) return profile.simulatedPermissions.contains(permission);
        return permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)
                || permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)
                || permission.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private void installEnvironmentTraceHooks() {
        try {
            Method exists = File.class.getDeclaredMethod("exists");
            hook(exists).intercept(chain -> {
                File file = (File) chain.getThisObject();
                if (file != null && looksSensitive(file.getAbsolutePath())) trace("file", file.getAbsolutePath());
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "File.exists trace hook failed: " + t);
        }
        try {
            Method exec = Runtime.class.getDeclaredMethod("exec", String.class);
            hook(exec).intercept(chain -> {
                Object value = chain.getArg(0);
                String cmd = value == null ? "" : String.valueOf(value);
                if (looksSensitive(cmd)) trace("exec", cmd);
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Runtime.exec trace hook failed: " + t);
        }
    }

    private static boolean looksSensitive(String value) {
        if (value == null) return false;
        String s = value.toLowerCase(Locale.ROOT);
        return s.contains("/proc/") || s.contains("magisk") || s.contains("kernelsu")
                || s.contains("apatch") || s.contains("xposed") || s.contains("lsposed")
                || s.contains("frida") || s.endsWith("/su") || s.contains("which su")
                || s.contains("mount") || s.contains("getenforce") || s.contains("getprop");
    }

    private void trace(String type, String value) {
        long ts = System.currentTimeMillis();
        String safe = value == null ? "" : value.replace('"', '\'');
        log(Log.INFO, TAG, "{\"ts\":" + ts + ",\"type\":\"" + type + "\",\"value\":\"" + safe + "\"}");
    }
}
