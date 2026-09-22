package com.yagay.ypower.hook.provider;

import com.yagay.ypower.hook.YPowerModule;
import com.yagay.ypower.model.AppProfile;

public interface HookProvider {
    String id();
    boolean isEnabled(AppProfile profile);
    void install(YPowerModule module, String packageName, AppProfile profile);
}
