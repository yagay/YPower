package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class PropertyTraceHookProvider implements HookProvider {
    @Override public String id() { return "property-trace"; }
    @Override public boolean isEnabled(AppProfile profile) { return profile.traceProperties; }
    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installPropertyTraceHooks(profile);
    }
}
