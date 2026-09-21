package com.yagay.ypower;

import android.app.Application;
import android.util.Log;

import com.topjohnwu.superuser.Shell;
import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.xposed.XposedBridgeManager;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class YPowerApplication extends Application implements XposedServiceHelper.OnServiceListener {
    @Override
    public void onCreate() {
        super.onCreate();
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setContext(this)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(15));
        try {
            XposedServiceHelper.registerListener(this);
        } catch (Throwable t) {
            Log.w("YPower", "LSPosed service registration unavailable", t);
        }
    }

    @Override
    public void onServiceBind(XposedService service) {
        XposedBridgeManager.setService(service);
        ProfileStore.get(this).syncAllToRemote();
        for (String pkg : ProfileStore.get(this).getEnabledPackages()) {
            XposedBridgeManager.requestScope(pkg);
        }
    }

    @Override
    public void onServiceDied(XposedService service) {
        XposedBridgeManager.setService(null);
    }
}
