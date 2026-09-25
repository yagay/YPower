package com.yagay.ypower.hook;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.util.Log;

import com.yagay.ypower.hook.provider.CommandTraceHookProvider;
import com.yagay.ypower.hook.provider.DebuggerTraceHookProvider;
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

    private String activePackageName = "";

    private static final List<HookProvider> PROVIDERS = List.of(
            new IdentityHookProvider(),
            new PermissionHookProvider(),
            new PackageScanHookProvider(),
            new FileTraceHookProvider(),
            new CommandTraceHookProvider(),
            new PropertyTraceHookProvider(),
            new DebuggerTraceHookProvider()
    );

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;

        String pkg = param.getPackageName();
        if (pkg == null || pkg.equals("com.yagay.ypower")) return;

        AppProfile profile = loadProfile(pkg);
        if (!profile.enabled) return;

        activePackageName = pkg;
        traceMeta(profile, "module", "enabled package=" + pkg, "module");

        for (HookProvider provider : PROVIDERS) {
            if (!provider.isEnabled(profile)) continue;
            try {
                provider.install(this, pkg, profile);
                traceMeta(profile, "provider", "installed=" + provider.id(), "module");
            } catch (Throwable t) {
                log(Log.WARN, TAG, "provider " + provider.id() + " failed: " + t);
                Log.w(TAG, "provider " + provider.id() + " failed", t);
            }
        }

        if (profile.traceNative) {
            boolean enabled = NativeTraceBridge.enable(pkg, profile.diagnosticSessionId);
            traceMeta(
                    profile,
                    "provider",
                    "native-bytehook=" + (enabled ? "enabled" : "unavailable"),
                    "NativeTraceBridge"
            );
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

            AppProfile p = AppProfile.fromJson(
                    prefs.getString("profile:" + packageName, null),
                    packageName
            );
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
                            copy.flags |= ApplicationInfo.FLAG_SYSTEM
                                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
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
                long startNs = System.nanoTime();
                try {
                    Object raw = chain.proceed();
                    int actual = raw instanceof Integer ? (Integer) raw : Integer.MIN_VALUE;

                    if (profile.tracePermissions) {
                        traceCall(
                                profile,
                                "permission",
                                DetectionRuleIds.PERMISSION_QUERY,
                                permission,
                                String.valueOf(actual),
                                actual != PackageManager.PERMISSION_GRANTED,
                                "",
                                "Context.checkSelfPermission",
                                startNs,
                                false
                        );
                    }

                    if (shouldSimulatePermission(profile, permission)) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                    return raw;
                } catch (Throwable t) {
                    if (profile.tracePermissions) {
                        traceCall(
                                profile,
                                "permission",
                                DetectionRuleIds.PERMISSION_QUERY,
                                permission,
                                "",
                                false,
                                throwableText(t),
                                "Context.checkSelfPermission",
                                startNs,
                                false
                        );
                    }
                    throw t;
                }
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
                    long startNs = System.nanoTime();

                    try {
                        Object raw = chain.proceed();
                        int actual = raw instanceof Integer ? (Integer) raw : Integer.MIN_VALUE;

                        if (profile.tracePermissions && packageName.equals(queriedPackage)) {
                            traceCall(
                                    profile,
                                    "permission",
                                    DetectionRuleIds.PERMISSION_QUERY,
                                    permission + " package=" + queriedPackage,
                                    String.valueOf(actual),
                                    actual != PackageManager.PERMISSION_GRANTED,
                                    "",
                                    "PackageManager.checkPermission",
                                    startNs,
                                    false
                            );
                        }

                        if (packageName.equals(queriedPackage)
                                && shouldSimulatePermission(profile, permission)) {
                            return PackageManager.PERMISSION_GRANTED;
                        }
                        return raw;
                    } catch (Throwable t) {
                        if (profile.tracePermissions && packageName.equals(queriedPackage)) {
                            traceCall(
                                    profile,
                                    "permission",
                                    DetectionRuleIds.PERMISSION_QUERY,
                                    permission + " package=" + queriedPackage,
                                    "",
                                    false,
                                    throwableText(t),
                                    "PackageManager.checkPermission",
                                    startNs,
                                    false
                            );
                        }
                        throw t;
                    }
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
                    String input = collectArgs(method, chain);
                    String specificRule = DetectionRuleIds.forPackage(input);
                    boolean enumeration = isEnumerationMethod(name);
                    if (!enumeration && DetectionRuleIds.UNKNOWN.equals(specificRule)) {
                        return chain.proceed();
                    }

                    long startNs = System.nanoTime();
                    try {
                        Object result = chain.proceed();

                        String foundPackage = enumeration
                                ? firstSensitivePackage(result)
                                : firstSensitivePackage(input);

                        String ruleId;
                        if (!foundPackage.isEmpty()) {
                            ruleId = DetectionRuleIds.forPackage(foundPackage);
                        } else if (!DetectionRuleIds.UNKNOWN.equals(specificRule)) {
                            ruleId = specificRule;
                        } else {
                            ruleId = DetectionRuleIds.PACKAGE_ENUMERATION;
                        }

                        boolean matched = !foundPackage.isEmpty()
                                || (!DetectionRuleIds.UNKNOWN.equals(specificRule) && result != null);

                        traceCall(
                                profile,
                                "package",
                                ruleId,
                                name + " " + input,
                                summarizeResult(result),
                                matched,
                                "",
                                "ApplicationPackageManager." + name,
                                startNs,
                                false
                        );
                        return result;
                    } catch (Throwable t) {
                        traceCall(
                                profile,
                                "package",
                                DetectionRuleIds.UNKNOWN.equals(specificRule)
                                        ? DetectionRuleIds.PACKAGE_ENUMERATION
                                        : specificRule,
                                name + " " + input,
                                "",
                                false,
                                throwableText(t),
                                "ApplicationPackageManager." + name,
                                startNs,
                                false
                        );
                        throw t;
                    }
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
                    if (!looksSensitivePath(path)) return chain.proceed();

                    String ruleId = DetectionRuleIds.forPath(path);
                    long startNs = System.nanoTime();

                    try {
                        Object result = chain.proceed();
                        boolean matched = resultLooksPositive(result);

                        traceCall(
                                profile,
                                "file",
                                ruleId,
                                methodName + " " + path,
                                summarizeResult(result),
                                matched,
                                "",
                                "java.io.File." + methodName,
                                startNs,
                                false
                        );
                        return result;
                    } catch (Throwable t) {
                        traceCall(
                                profile,
                                "file",
                                ruleId,
                                methodName + " " + path,
                                "",
                                false,
                                throwableText(t),
                                "java.io.File." + methodName,
                                startNs,
                                false
                        );
                        throw t;
                    }
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
                    String command = method.getParameterCount() == 0
                            ? ""
                            : stringify(chain.getArg(0));

                    if (!looksSensitiveCommand(command)) return chain.proceed();

                    String ruleId = DetectionRuleIds.forCommand(command);
                    long startNs = System.nanoTime();

                    try {
                        Object result = chain.proceed();
                        traceCall(
                                profile,
                                "exec",
                                ruleId,
                                command,
                                result == null ? "" : result.getClass().getName(),
                                false,
                                "",
                                "Runtime.exec",
                                startNs,
                                false
                        );
                        return result;
                    } catch (Throwable t) {
                        traceCall(
                                profile,
                                "exec",
                                ruleId,
                                command,
                                "",
                                false,
                                throwableText(t),
                                "Runtime.exec",
                                startNs,
                                false
                        );
                        throw t;
                    }
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
                if (!looksSensitiveCommand(command)) return chain.proceed();

                String ruleId = DetectionRuleIds.forCommand(command);
                long startNs = System.nanoTime();

                try {
                    Object result = chain.proceed();
                    traceCall(
                            profile,
                            "exec",
                            ruleId,
                            command,
                            result == null ? "" : result.getClass().getName(),
                            false,
                            "",
                            "ProcessBuilder.start",
                            startNs,
                            false
                    );
                    return result;
                } catch (Throwable t) {
                    traceCall(
                            profile,
                            "exec",
                            ruleId,
                            command,
                            "",
                            false,
                            throwableText(t),
                            "ProcessBuilder.start",
                            startNs,
                            false
                    );
                    throw t;
                }
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "ProcessBuilder.start hook failed: " + t);
        }

        try {
            Method exit = System.class.getDeclaredMethod("exit", int.class);
            hook(exit).intercept(chain -> {
                long startNs = System.nanoTime();
                traceCall(
                        profile,
                        "exit",
                        DetectionRuleIds.EXIT_SYSTEM,
                        "status=" + chain.getArg(0),
                        "",
                        true,
                        "",
                        "java.lang.System.exit",
                        startNs,
                        true
                );
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "System.exit hook failed: " + t);
        }

        try {
            Method halt = Runtime.class.getDeclaredMethod("halt", int.class);
            hook(halt).intercept(chain -> {
                long startNs = System.nanoTime();
                traceCall(
                        profile,
                        "exit",
                        DetectionRuleIds.EXIT_HALT,
                        "status=" + chain.getArg(0),
                        "",
                        true,
                        "",
                        "java.lang.Runtime.halt",
                        startNs,
                        true
                );
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Runtime.halt hook failed: " + t);
        }

        try {
            Method kill = android.os.Process.class.getDeclaredMethod("killProcess", int.class);
            hook(kill).intercept(chain -> {
                long startNs = System.nanoTime();
                traceCall(
                        profile,
                        "exit",
                        DetectionRuleIds.EXIT_KILL_PROCESS,
                        "pid=" + chain.getArg(0),
                        "",
                        true,
                        "",
                        "android.os.Process.killProcess",
                        startNs,
                        true
                );
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
                    if (!looksSensitiveProperty(key)) return chain.proceed();

                    String ruleId = DetectionRuleIds.forProperty(key);
                    long startNs = System.nanoTime();

                    try {
                        Object result = chain.proceed();
                        String resultText = stringify(result);

                        traceCall(
                                profile,
                                "property",
                                ruleId,
                                key,
                                resultText,
                                DetectionRuleIds.propertyValueLooksMatched(ruleId, resultText),
                                "",
                                "System.getProperty",
                                startNs,
                                false
                        );
                        return result;
                    } catch (Throwable t) {
                        traceCall(
                                profile,
                                "property",
                                ruleId,
                                key,
                                "",
                                false,
                                throwableText(t),
                                "System.getProperty",
                                startNs,
                                false
                        );
                        throw t;
                    }
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "System.getProperty hooks failed: " + t);
        }

        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            for (Method method : properties.getDeclaredMethods()) {
                String name = method.getName();
                if (!("get".equals(name)
                        || "getBoolean".equals(name)
                        || "getInt".equals(name)
                        || "getLong".equals(name))) {
                    continue;
                }
                if (method.getParameterCount() < 1) continue;

                hook(method).intercept(chain -> {
                    String key = stringArg(chain.getArg(0));
                    if (!looksSensitiveProperty(key)) return chain.proceed();

                    String ruleId = DetectionRuleIds.forProperty(key);
                    long startNs = System.nanoTime();

                    try {
                        Object result = chain.proceed();
                        String resultText = stringify(result);

                        traceCall(
                                profile,
                                "property",
                                ruleId,
                                key,
                                resultText,
                                DetectionRuleIds.propertyValueLooksMatched(ruleId, resultText),
                                "",
                                "SystemProperties." + name,
                                startNs,
                                false
                        );
                        return result;
                    } catch (Throwable t) {
                        traceCall(
                                profile,
                                "property",
                                ruleId,
                                key,
                                "",
                                false,
                                throwableText(t),
                                "SystemProperties." + name,
                                startNs,
                                false
                        );
                        throw t;
                    }
                });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "SystemProperties hooks failed: " + t);
        }
    }

    public void installDebuggerTraceHooks(AppProfile profile) {
        try {
            Method isConnected = Debug.class.getDeclaredMethod("isDebuggerConnected");
            hook(isConnected).intercept(chain -> {
                long startNs = System.nanoTime();
                try {
                    Object result = chain.proceed();
                    boolean matched = Boolean.TRUE.equals(result);
                    traceCall(
                            profile,
                            "debugger",
                            DetectionRuleIds.DEBUG_IS_CONNECTED,
                            "Debug.isDebuggerConnected",
                            String.valueOf(result),
                            matched,
                            "",
                            "android.os.Debug.isDebuggerConnected",
                            startNs,
                            false
                    );
                    return result;
                } catch (Throwable t) {
                    traceCall(
                            profile,
                            "debugger",
                            DetectionRuleIds.DEBUG_IS_CONNECTED,
                            "Debug.isDebuggerConnected",
                            "",
                            false,
                            throwableText(t),
                            "android.os.Debug.isDebuggerConnected",
                            startNs,
                            false
                    );
                    throw t;
                }
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Debug.isDebuggerConnected hook failed: " + t);
        }

        try {
            Method waiting = Debug.class.getDeclaredMethod("waitingForDebugger");
            hook(waiting).intercept(chain -> {
                long startNs = System.nanoTime();
                try {
                    Object result = chain.proceed();
                    boolean matched = Boolean.TRUE.equals(result);
                    traceCall(
                            profile,
                            "debugger",
                            DetectionRuleIds.DEBUG_WAITING,
                            "Debug.waitingForDebugger",
                            String.valueOf(result),
                            matched,
                            "",
                            "android.os.Debug.waitingForDebugger",
                            startNs,
                            false
                    );
                    return result;
                } catch (Throwable t) {
                    traceCall(
                            profile,
                            "debugger",
                            DetectionRuleIds.DEBUG_WAITING,
                            "Debug.waitingForDebugger",
                            "",
                            false,
                            throwableText(t),
                            "android.os.Debug.waitingForDebugger",
                            startNs,
                            false
                    );
                    throw t;
                }
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Debug.waitingForDebugger hook failed: " + t);
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
                    if (!looksSensitivePath(path)) return chain.proceed();

                    String ruleId = DetectionRuleIds.forPath(path);
                    long startNs = System.nanoTime();

                    try {
                        Object result = chain.proceed();
                        traceCall(
                                profile,
                                "file",
                                ruleId,
                                path,
                                "opened",
                                true,
                                "",
                                source,
                                startNs,
                                false
                        );
                        return result;
                    } catch (Throwable t) {
                        traceCall(
                                profile,
                                "file",
                                ruleId,
                                path,
                                "",
                                false,
                                throwableText(t),
                                source,
                                startNs,
                                false
                        );
                        throw t;
                    }
                });
            } catch (Throwable t) {
                log(Log.WARN, TAG, source + " constructor hook failed: " + t);
            }
        }
    }

    private boolean shouldSimulatePermission(AppProfile profile, String permission) {
        if (!profile.simulatePermissions || permission == null) return false;
        if (!profile.simulatedPermissions.isEmpty()) {
            return profile.simulatedPermissions.contains(permission);
        }
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
        return "getInstalledPackages".equals(name)
                || "getInstalledApplications".equals(name);
    }

    private static boolean looksSensitivePackageQuery(String value) {
        return !DetectionRuleIds.UNKNOWN.equals(DetectionRuleIds.forPackage(value));
    }

    private static boolean looksSensitivePath(String value) {
        String s = lower(value);
        return s.contains("/proc/")
                || s.contains("/data/adb")
                || s.contains("magisk")
                || s.contains("kernelsu")
                || s.contains("apatch")
                || s.contains("xposed")
                || s.contains("lsposed")
                || s.contains("frida")
                || s.endsWith("/su")
                || s.contains("/system/bin/su")
                || s.contains("/system/xbin/su");
    }

    private static boolean looksSensitiveCommand(String value) {
        String s = lower(value);
        return s.contains("which su")
                || s.matches(".*(^|\\s|/)su(\\s|$).*")
                || s.contains("getprop")
                || s.contains("mount")
                || s.contains("getenforce")
                || s.contains("/proc/")
                || s.contains("magisk")
                || s.contains("kernelsu")
                || s.contains("apatch")
                || s.contains("xposed")
                || s.contains("lsposed")
                || s.contains("frida");
    }

    private static boolean looksSensitiveProperty(String value) {
        String s = lower(value);
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

    private static String collectArgs(Method method, Object chain) {
        StringBuilder args = new StringBuilder();
        try {
            Method getArg = chain.getClass().getMethod("getArg", int.class);
            for (int i = 0; i < method.getParameterCount(); i++) {
                if (i > 0) args.append(" | ");
                args.append(stringify(getArg.invoke(chain, i)));
            }
        } catch (Throwable ignored) {
        }
        return args.toString();
    }

    private static String firstSensitivePackage(Object result) {
        if (!(result instanceof List<?>)) return "";
        for (Object item : (List<?>) result) {
            String pkg = packageNameOf(item);
            if (!DetectionRuleIds.UNKNOWN.equals(DetectionRuleIds.forPackage(pkg))) {
                return pkg;
            }
        }
        return "";
    }

    private static String firstSensitivePackage(String input) {
        if (!DetectionRuleIds.UNKNOWN.equals(DetectionRuleIds.forPackage(input))) {
            return input;
        }
        return "";
    }

    private static String packageNameOf(Object item) {
        if (item instanceof PackageInfo) return ((PackageInfo) item).packageName;
        if (item instanceof ApplicationInfo) return ((ApplicationInfo) item).packageName;
        return "";
    }

    private static boolean resultLooksPositive(Object result) {
        if (result instanceof Boolean) return (Boolean) result;
        if (result instanceof Object[]) return ((Object[]) result).length > 0;
        if (result instanceof List<?>) return !((List<?>) result).isEmpty();
        return result != null;
    }

    private static String summarizeResult(Object result) {
        if (result == null) return "null";
        if (result instanceof Boolean
                || result instanceof Number
                || result instanceof CharSequence) {
            return String.valueOf(result);
        }
        if (result instanceof Object[]) {
            return "array(length=" + ((Object[]) result).length + ")";
        }
        if (result instanceof List<?>) {
            return "list(size=" + ((List<?>) result).size() + ")";
        }
        if (result instanceof PackageInfo) {
            return "PackageInfo(" + ((PackageInfo) result).packageName + ")";
        }
        if (result instanceof ApplicationInfo) {
            return "ApplicationInfo(" + ((ApplicationInfo) result).packageName + ")";
        }
        return result.getClass().getName();
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

    private static String throwableText(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        return t.getClass().getName() + (msg == null ? "" : ": " + msg);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private void traceMeta(AppProfile profile, String type, String value, String source) {
        traceCall(
                profile,
                type,
                DetectionRuleIds.UNKNOWN,
                value,
                "",
                false,
                "",
                source,
                System.nanoTime(),
                false
        );
    }

    private void traceCall(
            AppProfile profile,
            String type,
            String ruleId,
            String input,
            String result,
            boolean matched,
            String exception,
            String source,
            long startNs,
            boolean forceStack
    ) {
        long ts = System.currentTimeMillis();
        long durationNs = Math.max(0L, System.nanoTime() - startNs);
        String stack = forceStack
                ? captureStack(32)
                : (profile != null && profile.traceStacks ? captureStack(12) : "");

        int pid = android.os.Process.myPid();
        int tid = android.os.Process.myTid();
        Thread thread = Thread.currentThread();

        String processName;
        try {
            processName = Application.getProcessName();
        } catch (Throwable ignored) {
            processName = "";
        }

        String sessionId = profile == null || profile.diagnosticSessionId == null
                ? ""
                : profile.diagnosticSessionId;

        String json = "{\"ts\":" + ts
                + ",\"package\":\"" + escapeJson(activePackageName) + "\""
                + ",\"sessionId\":\"" + escapeJson(sessionId) + "\""
                + ",\"type\":\"" + escapeJson(type) + "\""
                + ",\"ruleId\":\"" + escapeJson(ruleId) + "\""
                + ",\"input\":\"" + escapeJson(input) + "\""
                + ",\"value\":\"" + escapeJson(input) + "\""
                + ",\"result\":\"" + escapeJson(result) + "\""
                + ",\"matched\":" + matched
                + ",\"exception\":\"" + escapeJson(exception) + "\""
                + ",\"source\":\"" + escapeJson(source) + "\""
                + ",\"pid\":" + pid
                + ",\"tid\":" + tid
                + ",\"thread\":\"" + escapeJson(thread.getName()) + "\""
                + ",\"process\":\"" + escapeJson(processName) + "\""
                + ",\"durationNs\":" + durationNs
                + ",\"stack\":\"" + escapeJson(stack) + "\"}";

        log(Log.INFO, TAG, json);
        Log.i(TAG, json);
    }

    private static String captureStack(int maxFrames) {
        StringBuilder out = new StringBuilder();
        int added = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String cls = frame.getClassName();
            if (cls.startsWith("java.lang.Thread")
                    || cls.startsWith("com.yagay.ypower")) {
                continue;
            }

            if (added++ > 0) out.append(" <- ");
            out.append(frame.getClassName())
                    .append('.')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber());

            if (added >= maxFrames) break;
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
