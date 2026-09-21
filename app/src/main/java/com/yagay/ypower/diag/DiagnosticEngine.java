package com.yagay.ypower.diag;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;

import com.yagay.ypower.model.DiagnosticFinding;
import com.yagay.ypower.model.DiagnosticLevel;
import com.yagay.ypower.model.DiagnosticReport;
import com.yagay.ypower.model.DiagnosticStatus;
import com.yagay.ypower.root.RootShell;
import com.yagay.ypower.util.ShellEscaper;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiagnosticEngine {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private DiagnosticEngine() {}

    public interface Callback { void onComplete(DiagnosticReport report); }

    public static void runAsync(Context context, String packageName, DiagnosticLevel level, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> callback.onComplete(run(app, packageName, level)));
    }

    public static DiagnosticReport run(Context context, String packageName, DiagnosticLevel level) {
        DiagnosticReport report = new DiagnosticReport(packageName, level);
        PackageManager pm = context.getPackageManager();
        boolean root = RootShell.isRootAvailable();
        add(report, "root", "Root 权限", root ? DiagnosticStatus.FAIL : DiagnosticStatus.PASS,
                root ? "YPower 当前可以获得 root shell" : "未获得 root shell");

        scanKnownPackages(pm, report);
        scanRootPaths(report);
        scanBuild(report);
        scanSelinux(report);
        scanMounts(report, level);
        scanDeveloperState(context, report);
        scanNetwork(context, report);
        scanPackageMetadata(pm, packageName, report);
        scanExitInfo(context, packageName, report);
        scanLogcat(packageName, report, level);

        if (level != DiagnosticLevel.QUICK) scanProcess(packageName, report, level);
        if (level == DiagnosticLevel.DEEP) scanDeepProc(packageName, report);

        add(report, "integrity", "Play Integrity", DiagnosticStatus.UNKNOWN,
                "Google/服务端判定不能仅凭本地环境扫描还原具体 verdict");
        add(report, "integrity", "Key Attestation", DiagnosticStatus.UNKNOWN,
                "需要应用自身或专门 attestation 流程提供结果");
        add(report, "server", "服务端风控", DiagnosticStatus.UNKNOWN,
                "如果退出由服务端决定，本地通常只能定位到请求/响应阶段");

        CorrelationEngine.analyze(report);
        return report;
    }

    private static void scanKnownPackages(PackageManager pm, DiagnosticReport report) {
        List<String> foundRoot = installed(pm, DetectionRules.ROOT_PACKAGES);
        add(report, "root", "Root 管理应用", foundRoot.isEmpty() ? DiagnosticStatus.PASS : DiagnosticStatus.FAIL,
                foundRoot.isEmpty() ? "未发现常见 Root 管理应用" : String.join(", ", foundRoot));
        List<String> hook = installed(pm, DetectionRules.HOOK_PACKAGES);
        add(report, "hook", "LSPosed/Xposed", hook.isEmpty() ? DiagnosticStatus.PASS : DiagnosticStatus.WARN,
                hook.isEmpty() ? "未发现常见 Hook 管理应用" : String.join(", ", hook));
        List<String> virtual = installed(pm, DetectionRules.VIRTUAL_PACKAGES);
        add(report, "virtual", "虚拟空间/双开", virtual.isEmpty() ? DiagnosticStatus.PASS : DiagnosticStatus.WARN,
                virtual.isEmpty() ? "未发现常见虚拟空间应用" : String.join(", ", virtual));
    }

    private static void scanRootPaths(DiagnosticReport report) {
        if (!RootShell.isRootAvailable()) {
            add(report, "root", "Root 路径", DiagnosticStatus.UNKNOWN, "无 root，无法完整扫描受保护路径");
            return;
        }
        List<String> found = new ArrayList<>();
        for (String path : DetectionRules.ROOT_PATHS) {
            RootShell.CommandResult r = RootShell.exec("[ -e " + ShellEscaper.q(path) + " ] && echo " + ShellEscaper.q(path) + " || true");
            if (!r.text().isBlank()) found.add(path);
        }
        add(report, "root", "Root/Systemless 路径", found.isEmpty() ? DiagnosticStatus.PASS : DiagnosticStatus.FAIL,
                found.isEmpty() ? "未发现规则库中的路径" : String.join(", ", found));
    }

    private static void scanBuild(DiagnosticReport report) {
        boolean testKeys = Build.TAGS != null && Build.TAGS.contains("test-keys");
        add(report, "build", "Build Tags", testKeys ? DiagnosticStatus.WARN : DiagnosticStatus.PASS,
                String.valueOf(Build.TAGS));
        String props = RootShell.exec("getprop ro.boot.verifiedbootstate; getprop ro.boot.vbmeta.device_state; getprop ro.debuggable; getprop ro.secure").text();
        report.raw.add("[build-properties]\n" + props);
        String lower = props.toLowerCase(Locale.ROOT);
        DiagnosticStatus status = (lower.contains("orange") || lower.contains("unlocked"))
                ? DiagnosticStatus.WARN : DiagnosticStatus.PASS;
        add(report, "integrity", "Bootloader/AVB", status, props.isBlank() ? "属性不可用" : props);
    }

    private static void scanSelinux(DiagnosticReport report) {
        String mode = RootShell.exec("getenforce 2>/dev/null || getprop ro.build.selinux").text();
        add(report, "selinux", "SELinux", "Enforcing".equalsIgnoreCase(mode.trim()) ? DiagnosticStatus.PASS : DiagnosticStatus.WARN,
                mode.isBlank() ? "状态未知" : mode);
    }

    private static void scanMounts(DiagnosticReport report, DiagnosticLevel level) {
        if (!RootShell.isRootAvailable()) {
            add(report, "mount", "异常挂载", DiagnosticStatus.UNKNOWN, "无 root，无法完整读取 mount namespace");
            return;
        }
        String cmd = level == DiagnosticLevel.QUICK
                ? "mount | grep -Ei 'overlay|magisk|kernelsu|apatch|modules' | head -n 30 || true"
                : "cat /proc/1/mountinfo | grep -Ei 'overlay|magisk|kernelsu|apatch|modules' | head -n 60 || true";
        String out = RootShell.exec(cmd).text();
        report.raw.add("[mount]\n" + out);
        add(report, "mount", "Systemless/Overlay 挂载", out.isBlank() ? DiagnosticStatus.PASS : DiagnosticStatus.WARN,
                out.isBlank() ? "未发现规则库关键词" : "发现可疑挂载，详细结果中查看原始记录");
    }

    private static void scanDeveloperState(Context context, DiagnosticReport report) {
        try {
            int dev = Settings.Global.getInt(context.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
            int adb = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            add(report, "settings", "开发者选项", dev == 0 ? DiagnosticStatus.PASS : DiagnosticStatus.WARN, "enabled=" + dev);
            add(report, "settings", "ADB", adb == 0 ? DiagnosticStatus.PASS : DiagnosticStatus.WARN, "enabled=" + adb);
        } catch (Throwable t) {
            add(report, "settings", "开发者选项/ADB", DiagnosticStatus.UNKNOWN, t.toString());
        }
    }

    private static void scanNetwork(Context context, DiagnosticReport report) {
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = cm == null || cm.getActiveNetwork() == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            boolean vpn = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            add(report, "network", "VPN/Tunnel", vpn ? DiagnosticStatus.WARN : DiagnosticStatus.PASS,
                    vpn ? "当前活动网络包含 VPN transport" : "未发现活动 VPN transport");
            String host = System.getProperty("http.proxyHost", "");
            String port = System.getProperty("http.proxyPort", "");
            boolean proxy = !host.isBlank();
            add(report, "network", "系统代理", proxy ? DiagnosticStatus.WARN : DiagnosticStatus.PASS,
                    proxy ? host + ":" + port : "未设置 Java HTTP proxy");
        } catch (Throwable t) {
            add(report, "network", "网络环境", DiagnosticStatus.UNKNOWN, t.toString());
        }
    }

    private static void scanPackageMetadata(PackageManager pm, String packageName, DiagnosticReport report) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_PERMISSIONS);
            add(report, "package", "目标应用", DiagnosticStatus.PASS,
                    "uid=" + ai.uid + ", sourceDir=" + ai.sourceDir + ", version=" + pi.versionName);
            if (pi.signingInfo != null && pi.signingInfo.getApkContentsSigners().length > 0) {
                byte[] cert = pi.signingInfo.getApkContentsSigners()[0].toByteArray();
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                add(report, "signature", "APK 签名", DiagnosticStatus.PASS, hex(md.digest(cert)));
            }
            if (pi.requestedPermissions != null) {
                add(report, "permission", "Manifest 权限", DiagnosticStatus.PASS, "声明 " + pi.requestedPermissions.length + " 项");
            }
        } catch (Throwable t) {
            add(report, "package", "目标应用", DiagnosticStatus.FAIL, t.toString());
        }
    }

    private static void scanExitInfo(Context context, String packageName, DiagnosticReport report) {
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            if (am == null) return;
            List<ApplicationExitInfo> infos = am.getHistoricalProcessExitReasons(packageName, 0, 8);
            if (infos.isEmpty()) {
                add(report, "crash", "Process Exit Info", DiagnosticStatus.PASS, "没有可访问的近期退出记录");
                return;
            }
            StringBuilder b = new StringBuilder();
            for (ApplicationExitInfo info : infos) {
                b.append("reason=").append(info.getReason())
                        .append(" status=").append(info.getStatus())
                        .append(" importance=").append(info.getImportance())
                        .append(" description=").append(info.getDescription()).append('\n');
            }
            report.raw.add("[exit-info]\n" + b);
            add(report, "crash", "Process Exit Info", DiagnosticStatus.WARN, "发现 " + infos.size() + " 条近期退出记录");
        } catch (Throwable t) {
            add(report, "crash", "Process Exit Info", DiagnosticStatus.UNKNOWN, "系统未允许读取：" + t.getClass().getSimpleName());
        }
    }

    private static void scanLogcat(String packageName, DiagnosticReport report, DiagnosticLevel level) {
        if (!RootShell.isRootAvailable()) {
            add(report, "crash", "Logcat", DiagnosticStatus.UNKNOWN, "无 root，无法稳定读取其他应用完整 logcat");
            return;
        }
        int lines = level == DiagnosticLevel.DEEP ? 1200 : level == DiagnosticLevel.STANDARD ? 700 : 350;
        String filter = packageName + "|AndroidRuntime|DEBUG|ActivityManager|lmkd|avc: denied|YPowerTrace";
        String out = RootShell.exec("logcat -d -t " + lines + " | grep -Ei " + ShellEscaper.q(filter) + " | tail -n " + lines).text();
        report.raw.add("[logcat]\n" + out);
        boolean crash = false;
        for (String keyword : DetectionRules.CRASH_KEYWORDS) if (out.contains(keyword)) { crash = true; break; }
        add(report, "crash", "Java/Native/ANR 日志", crash ? DiagnosticStatus.FAIL : DiagnosticStatus.PASS,
                crash ? "发现崩溃/ANR/权限/SELinux 等异常关键词" : "当前采样未发现规则库异常关键词");
    }

    private static void scanProcess(String packageName, DiagnosticReport report, DiagnosticLevel level) {
        if (!RootShell.isRootAvailable()) return;
        String pid = RootShell.exec("pidof " + ShellEscaper.q(packageName) + " || true").text().trim();
        if (pid.isBlank()) {
            add(report, "process", "运行进程", DiagnosticStatus.UNKNOWN, "目标应用当前未运行");
            return;
        }
        String firstPid = pid.split("\\s+")[0];
        String status = RootShell.exec("cat /proc/" + firstPid + "/status 2>/dev/null || true").text();
        report.raw.add("[proc-status pid=" + firstPid + "]\n" + status);
        add(report, "process", "运行进程", DiagnosticStatus.PASS, "pid=" + firstPid);
        if (level != DiagnosticLevel.QUICK) {
            String maps = RootShell.exec("grep -Ei 'lsposed|xposed|zygisk|riru|frida|shadowhook|bytehook|libxposed' /proc/" + firstPid + "/maps 2>/dev/null | head -n 80 || true").text();
            report.raw.add("[proc-maps]\n" + maps);
            add(report, "hook", "进程注入/Maps", maps.isBlank() ? DiagnosticStatus.PASS : DiagnosticStatus.WARN,
                    maps.isBlank() ? "未发现规则库关键词" : "maps 中发现 Hook/注入相关关键词");
        }
    }

    private static void scanDeepProc(String packageName, DiagnosticReport report) {
        if (!RootShell.isRootAvailable()) return;
        String pid = RootShell.exec("pidof " + ShellEscaper.q(packageName) + " || true").text().trim();
        if (pid.isBlank()) return;
        String firstPid = pid.split("\\s+")[0];
        String threads = RootShell.exec("for t in /proc/" + firstPid + "/task/*/comm; do cat \"$t\" 2>/dev/null; done | head -n 200").text();
        String fds = RootShell.exec("ls -l /proc/" + firstPid + "/fd 2>/dev/null | head -n 200 || true").text();
        String mem = RootShell.exec("cat /proc/" + firstPid + "/smaps_rollup 2>/dev/null || cat /proc/" + firstPid + "/status 2>/dev/null").text();
        report.raw.add("[threads]\n" + threads);
        report.raw.add("[fds]\n" + fds);
        report.raw.add("[memory]\n" + mem);
        String lower = threads.toLowerCase(Locale.ROOT);
        boolean suspicious = DetectionRules.MAP_KEYWORDS.stream().anyMatch(lower::contains);
        add(report, "hook", "线程特征", suspicious ? DiagnosticStatus.WARN : DiagnosticStatus.PASS,
                suspicious ? "线程名中发现注入/Hook 关键词" : "当前线程名未发现规则库关键词");
        add(report, "resource", "FD/Memory 快照", DiagnosticStatus.PASS, "深度模式已采集到原始报告");
    }

    private static List<String> installed(PackageManager pm, List<String> packages) {
        List<String> found = new ArrayList<>();
        for (String pkg : packages) {
            try { pm.getPackageInfo(pkg, 0); found.add(pkg); }
            catch (PackageManager.NameNotFoundException ignored) {}
        }
        return found;
    }

    private static void add(DiagnosticReport report, String category, String title, DiagnosticStatus status, String summary) {
        report.findings.add(new DiagnosticFinding(category + "." + title.hashCode(), category, title, status, summary));
    }

    private static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte value : bytes) b.append(String.format(Locale.ROOT, "%02X", value));
        return b.toString();
    }
}
