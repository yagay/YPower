package com.yagay.ypower.root;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.model.AppProfile;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        for (String pkg : ProfileStore.get(context).getEnabledPackages()) {
            AppProfile profile = ProfileStore.get(context).getProfile(pkg);
            EnhancementEngine.applyAsync(context, profile, null);
        }
    }
}
