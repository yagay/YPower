package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class ExceptionTraceHookProvider implements HookProvider {
    @Override public String id() { return "exception-trace"; }

    @Override public boolean isEnabled(AppProfile profile) {
        return profile.traceExceptions;
    }

    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installExceptionTraceHooks(profile);
    }
}
