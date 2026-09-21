package com.yagay.ypower.root;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

import com.yagay.ypower.model.AppProfile;
import com.yagay.ypower.util.ShellEscaper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EnhancementEngine {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private EnhancementEngine() {}

    public interface Callback { void onComplete(ApplyResult result); }

    public static void applyAsync(Context context, AppProfile profile, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            ApplyResult result = apply(app, profile);
            if (callback != null) callback.onComplete(result);
        });
    }

    public static ApplyResult apply(Context context, AppProfile p) {
        ApplyResult result = new ApplyResult();
        if (!RootShell.isRootAvailable()) {
            result.errors.add("Root unavailable");
            return result;
        }
        final String pkg = ShellEscaper.q(p.packageName);
        final int uid;
        try {
            uid = context.getPackageManager().getApplicationInfo(p.packageName, 0).uid;
        } catch (Exception e) {
            result.errors.add("Package not found: " + p.packageName);
            return result;
        }

        run(result, "Doze", p.dozeWhitelist
                ? "cmd deviceidle whitelist +" + pkg
                : "cmd deviceidle whitelist -" + pkg);

        if (p.backgroundOps) {
            run(result, "RUN_IN_BACKGROUND", "cmd appops set " + pkg + " RUN_IN_BACKGROUND allow || true");
            run(result, "RUN_ANY_IN_BACKGROUND", "cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow || true");
            run(result, "START_FOREGROUND", "cmd appops set " + pkg + " START_FOREGROUND allow || true");
        } else {
            run(result, "RUN_IN_BACKGROUND reset", "cmd appops set " + pkg + " RUN_IN_BACKGROUND default || true");
            run(result, "RUN_ANY_IN_BACKGROUND reset", "cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND default || true");
        }

        if (p.standbyActive) {
            run(result, "Standby active", "am set-inactive " + pkg + " false; am set-standby-bucket " + pkg + " active || true");
        }

        if (p.backgroundData) {
            run(result, "Background data", "cmd netpolicy add restrict-background-whitelist " + uid + " || true");
        }

        if (p.autoGrantDangerous) grantDeclaredDangerousPermissions(context, p.packageName, result);
        return result;
    }

    private static void grantDeclaredDangerousPermissions(Context context, String packageName, ApplyResult result) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions == null) return;
            for (String permission : pi.requestedPermissions) {
                try {
                    PermissionInfo info = context.getPackageManager().getPermissionInfo(permission, 0);
                    int base = info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                    if (base != PermissionInfo.PROTECTION_DANGEROUS) continue;
                    RootShell.CommandResult r = RootShell.exec("pm grant --user current " + ShellEscaper.q(packageName) + " " + ShellEscaper.q(permission));
                    if (r.ok()) result.applied.add("Granted " + permission);
                    else result.notes.add("Could not grant " + permission + ": " + r.text());
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        } catch (Exception e) {
            result.errors.add("Permission scan failed: " + e);
        }
    }

    private static void run(ApplyResult result, String label, String command) {
        RootShell.CommandResult r = RootShell.exec(command);
        if (r.ok()) result.applied.add(label);
        else result.notes.add(label + ": " + r.text());
    }

    public static final class ApplyResult {
        public final List<String> applied = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public boolean success() { return errors.isEmpty(); }
        public String summary() { return "Applied=" + applied.size() + ", Notes=" + notes.size() + ", Errors=" + errors.size(); }
    }
}
