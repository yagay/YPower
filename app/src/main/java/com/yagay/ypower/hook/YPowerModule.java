package com.yagay.ypower.hook;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.yagay.ypower.hook.provider.CommandTraceHookProvider;
import com.yagay.ypower.hook.provider.FileTraceHookProvider;
import com.yagay.ypower.hook.provider.HookProvider;
import com.yagay.ypower.hook.provider.IdentityHookProvider;
import com.yagay.ypower.hook.provider.PackageScanHookProvider;
import com.yagay.ypower.hook.provider.PermissionHookProvider;
import com.yagay.ypower.hook.provider.PropertyTraceHookProvider;
import com.yagay.ypower.model.AppProfile;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class YPowerModule extends XposedModule {
    private static final String TAG = "YPowerTrace";
    private static final String GROUP = "ypower";

    private static final List<HookProvider> PROVIDERS = List.of(
            new IdentityHookProvider(),
            new PermissionHookProvider(),
            new PackageScanHookProvider(),
            new FileTraceHookProvider(),
            new CommandTraceHookProvider(),
            new PropertyTraceHookProvider()
    );

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;
        String pkg = param.getPackageName();
        if (pkg == null || pkg.equals("com.yagay.ypower")) return;

        AppProfile profile = loadProfile(pkg);
        if (!profile.enabled) return;

        trace(profile, "module", "enabled package=" + pkg, "module");

        for (HookProvider provider : PROVIDERS) {
            if (!provider.isEnabled(profile)) continue;
            try {
                provider.install(this, pkg, profile);
                trace(profile, "provider", "installed=" + provider.id(), "module");
            } catch (Throwable t) {
                log(Log.WARN, TAG, "provider " + provider.id() + " failed: " + t);
                Log.w(TAG, "provider " + provider.id() + " failed", t);
            }
        }
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

    public void installIdentityHooks(String packageName) {
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

        try {
            Class<?> apm = Class.forName("android.app.ApplicationPackageManager");
            for (Method method : apm.getDeclaredMethods()) {
                if (!"getApplicationInfo".equals(method.getName())) continue;
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof ApplicationInfo) {
                        ApplicationInfo original = (ApplicationInfo) result;
                        if (packageName.equals(original.packageName)) {
                            ApplicationInfo copy = new ApplicationInfo(original);
                            copy.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
                            return copy;
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "ApplicationPackageManager identity hook failed: " + t);
        }
    }

    public void installPermissionHooks(String packageName, AppProfile profile) {
        try {
            Method method = Context.class.getDeclaredMethod("checkSelfPermission", String.class);
            hook(method).intercept(chain -> {
                String permission = stringArg(chain.getArg(0));
                if (shouldSimulatePermission(profile, permission)) {
                    trace(profile, "permission", permission, "Context.checkSelfPermission");
                    return PackageManager.PERMISSION_GRANTED;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "checkSelfPermission hook failed: " + t);
        }

        try {
            Class<?> apm = Class.forName("android.app.ApplicationPackageManager");
            for (Method method : apm.getDeclaredMethods()) {
                if (!"checkPermission".equals(method.getName())) continue;
                if (method.getParameterCount() < 2) continue;
                hook(method).intercept(chain -> {
                    String permission = stringArg(chain.getArg(0));
                    String queriedPackage = stringArg(chain.getArg(1));
                    if (packageName.equals(queriedPackage) && shouldSimulatePermission(profile, permission)) {
                        trace(profile, "permission", permission, "PackageManager.checkPermission");
                        return PackageManager.PERMISSION_GRANTED;
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "PackageManager permission hook failed: " + t);
        }
    }

    public void installPackageScanHooks(AppProfile profile) {
        try {
            Class<?> apm = Class.forName("android.app.ApplicationPackageManager");
            for (Method method : apm.getDeclaredMethods()) {
                String name = method.getName();
                if (!isPackageScanMethod(name)) continue;
                hook(method).intercept(chain -> {
                    StringBuilder args = new StringBuilder();
                    for (int i = 0; i < method.getParameterCount(); i++) {
                        if (i > 0) args.append(" | ");
                        args.append(stringify(chain.getArg(i)));
                    }
                    String value = args.toString();
                    if (isEnumerationMethod(name) || looksSensitivePackageQuery(value)) {
                        trace(profile, "package", name + " " + value, "ApplicationPackageManager");
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Package scan hook failed: " + t);
        }
    }

    public void installFileTraceHooks(AppProfile profile) {
        for (String methodName : new String[]{"exists", "canRead", "canExecute", "list", "listFiles"}) {
            try {
                Method method = File.class.getDeclaredMethod(methodName);
                hook(method).intercept(chain -> {
                    File file = (File) chain.getThisObject();
                    String path = file == null ? "" : file.getAbsolutePath();
                    if (looksSensitivePath(path)) {
                        trace(profile, "file", methodName + " " + path, "java.io.File");
                    }
                    return chain.proceed();
                });
            } catch (Throwable t) {
                log(Log.WARN, TAG, "File." + methodName + " hook failed: " + t);
            }
        }

        hookSensitiveConstructors(profile, FileInputStream.class, "FileInputStream");
        hookSensitiveConstructors(profile, RandomAccessFile.class, "RandomAccessFile");
    }

    public void installCommandTraceHooks(AppProfile profile) {
        try {
            for (Method method : Runtime.class.getDeclaredMethods()) {
                if (!"exec".equals(method.getName())) continue;
                hook(method).intercept(chain -> {
                    String command = method.getParameterCount() == 0 ? "" : stringify(chain.getArg(0));
                    if (looksSensitiveCommand(command)) {
                        trace(profile, "exec", command, "Runtime.exec");
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Runtime.exec hooks failed: " + t);
        }

        try {
            Method start = ProcessBuilder.class.getDeclaredMethod("start");
            hook(start).intercept(chain -> {
                ProcessBuilder builder = (ProcessBuilder) chain.getThisObject();
                String command = builder == null ? "" : String.join(" ", builder.command());
                if (looksSensitiveCommand(command)) {
                    trace(profile, "exec", command, "ProcessBuilder.start");
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "ProcessBuilder.start hook failed: " + t);
        }

        try {
            Method exit = System.class.getDeclaredMethod("exit", int.class);
            hook(exit).intercept(chain -> {
                trace(profile, "exit", "System.exit(" + chain.getArg(0) + ")", "java.lang.System");
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "System.exit hook failed: " + t);
        }

        try {
            Method halt = Runtime.class.getDeclaredMethod("halt", int.class);
            hook(halt).intercept(chain -> {
                trace(profile, "exit", "Runtime.halt(" + chain.getArg(0) + ")", "java.lang.Runtime");
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Runtime.halt hook failed: " + t);
        }

        try {
            Method kill = android.os.Process.class.getDeclaredMethod("killProcess", int.class);
            hook(kill).intercept(chain -> {
                trace(profile, "exit", "Process.killProcess(" + chain.getArg(0) + ")", "android.os.Process");
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Process.killProcess hook failed: " + t);
        }
    }

    public void installPropertyTraceHooks(AppProfile profile) {
        try {
            for (Method method : System.class.getDeclaredMethods()) {
                if (!"getProperty".equals(method.getName())) continue;
                if (method.getParameterCount() < 1) continue;
                hook(method).intercept(chain -> {
                    String key = stringArg(chain.getArg(0));
                    if (looksSensitiveProperty(key)) {
                        trace(profile, "property", key, "System.getProperty");
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "System.getProperty hooks failed: " + t);
        }

        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            for (Method method : properties.getDeclaredMethods()) {
                String name = method.getName();
                if (!("get".equals(name) || "getBoolean".equals(name)
                        || "getInt".equals(name) || "getLong".equals(name))) continue;
                if (method.getParameterCount() < 1) continue;
                hook(method).intercept(chain -> {
                    String key = stringArg(chain.getArg(0));
                    if (looksSensitiveProperty(key)) {
                        trace(profile, "property", key, "SystemProperties." + name);
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "SystemProperties hooks failed: " + t);
        }
    }

    private void hookSensitiveConstructors(AppProfile profile, Class<?> clazz, String source) {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (constructor.getParameterCount() < 1) continue;
            Class<?> first = constructor.getParameterTypes()[0];
            if (!(first == String.class || first == File.class)) continue;
            try {
                hook(constructor).intercept(chain -> {
                    String path = stringify(chain.getArg(0));
                    if (looksSensitivePath(path)) {
                        trace(profile, "file", path, source);
                    }
                    return chain.proceed();
                });
            } catch (Throwable t) {
                log(Log.WARN, TAG, source + " constructor hook failed: " + t);
            }
        }
    }

    private boolean shouldSimulatePermission(AppProfile profile, String permission) {
        if (!profile.simulatePermissions || permission == null) return false;
        if (!profile.simulatedPermissions.isEmpty()) return profile.simulatedPermissions.contains(permission);
        return permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)
                || permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)
                || permission.equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private static boolean isPackageScanMethod(String name) {
        return "getPackageInfo".equals(name)
                || "getApplicationInfo".equals(name)
                || "getInstalledPackages".equals(name)
                || "getInstalledApplications".equals(name)
                || "queryIntentActivities".equals(name)
                || "getInstallerPackageName".equals(name)
                || "getInstallSourceInfo".equals(name);
    }

    private static boolean isEnumerationMethod(String name) {
        return "getInstalledPackages".equals(name) || "getInstalledApplications".equals(name);
    }

    private static boolean looksSensitivePackageQuery(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return s.contains("magisk") || s.contains("kernelsu") || s.contains("apatch")
                || s.contains("xposed") || s.contains("lsposed") || s.contains("frida")
                || s.contains("shizuku") || s.contains("supersu") || s.contains("kingroot");
    }

    private static boolean looksSensitivePath(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return s.contains("/proc/") || s.contains("/data/adb") || s.contains("magisk")
                || s.contains("kernelsu") || s.contains("apatch") || s.contains("xposed")
                || s.contains("lsposed") || s.contains("frida") || s.endsWith("/su")
                || s.contains("/system/bin/su") || s.contains("/system/xbin/su");
    }

    private static boolean looksSensitiveCommand(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return s.contains("which su") || s.matches(".*(^|\\s|/)su(\\s|$).*")
                || s.contains("getprop") || s.contains("mount") || s.contains("getenforce")
                || s.contains("/proc/") || s.contains("magisk") || s.contains("kernelsu")
                || s.contains("apatch") || s.contains("xposed") || s.contains("lsposed")
                || s.contains("frida");
    }

    private static boolean looksSensitiveProperty(String value) {
        String s = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return s.startsWith("ro.boot.")
                || s.equals("ro.debuggable")
                || s.equals("ro.secure")
                || s.equals("ro.build.tags")
                || s.equals("ro.build.type")
                || s.equals("ro.build.user")
                || s.startsWith("ro.product.")
                || s.startsWith("ro.hardware.")
                || s.contains("magisk")
                || s.contains("kernelsu")
                || s.contains("apatch");
    }

    private static String stringArg(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof String[]) return String.join(" ", (String[]) value);
        if (value instanceof File) return ((File) value).getAbsolutePath();
        return String.valueOf(value);
    }

    private void trace(AppProfile profile, String type, String value, String source) {
        long ts = System.currentTimeMillis();
        String stack = profile != null && profile.traceStacks ? shortStack() : "";
        String json = "{\"ts\":" + ts
                + ",\"type\":\"" + escapeJson(type) + "\""
                + ",\"value\":\"" + escapeJson(value) + "\""
                + ",\"source\":\"" + escapeJson(source) + "\""
                + ",\"stack\":\"" + escapeJson(stack) + "\"}";
        log(Log.INFO, TAG, json);
        Log.i(TAG, json);
    }

    private static String shortStack() {
        StringBuilder out = new StringBuilder();
        int added = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String cls = frame.getClassName();
            if (cls.startsWith("java.lang.Thread") || cls.startsWith("com.yagay.ypower")) continue;
            if (added++ > 0) out.append(" <- ");
            out.append(frame.getClassName()).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
            if (added >= 8) break;
        }
        return out.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
