package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class CommandTraceHookProvider implements HookProvider {
    @Override public String id() { return "command-trace"; }
    @Override public boolean isEnabled(AppProfile profile) { return profile.traceCommands; }
    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installCommandTraceHooks(profile);
    }
}
