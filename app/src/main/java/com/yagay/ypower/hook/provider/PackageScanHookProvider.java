package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class PackageScanHookProvider implements HookProvider {
    @Override public String id() { return "package-scan"; }
    @Override public boolean isEnabled(AppProfile profile) { return profile.tracePackageScan; }
    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installPackageScanHooks(profile);
    }
}
