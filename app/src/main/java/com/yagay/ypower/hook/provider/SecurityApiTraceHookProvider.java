package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class SecurityApiTraceHookProvider implements HookProvider {
    @Override public String id() { return "security-api-trace"; }

    @Override public boolean isEnabled(AppProfile profile) {
        return profile.traceSecurityApis;
    }

    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installSecurityApiTraceHooks(profile);
    }
}
