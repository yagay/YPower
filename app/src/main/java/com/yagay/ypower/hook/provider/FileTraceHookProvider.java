package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class FileTraceHookProvider implements HookProvider {
    @Override public String id() { return "file-trace"; }
    @Override public boolean isEnabled(AppProfile profile) { return profile.traceFiles; }
    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installFileTraceHooks(profile);
    }
}
