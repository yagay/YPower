package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class DebuggerTraceHookProvider implements HookProvider {
    @Override public String id() { return "debugger-trace"; }

    @Override public boolean isEnabled(AppProfile profile) {
        return profile.traceDebugger;
    }

    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installDebuggerTraceHooks(profile);
    }
}
