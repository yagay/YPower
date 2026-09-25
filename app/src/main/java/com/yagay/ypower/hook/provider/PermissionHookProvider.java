package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class PermissionHookProvider implements HookProvider {
    @Override public String id() { return "permission"; }
    @Override public boolean isEnabled(AppProfile profile) {
        return profile.simulatePermissions || profile.tracePermissions;
    }
    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installPermissionHooks(packageName, profile);
    }
}
