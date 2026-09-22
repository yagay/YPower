package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public final class IdentityHookProvider implements HookProvider {
    @Override public String id() { return "identity"; }
    @Override public boolean isEnabled(AppProfile profile) { return profile.simulateSystemApp; }
    @Override public void install(YPowerModule module, String packageName, AppProfile profile) {
        module.installIdentityHooks(packageName);
    }
}
