package com.yagay.ypower.root;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import com.topjohnwu.superuser.ipc.RootService;

public class YPowerRootService extends RootService {
    public static final int MSG_PING = 1;

    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), this::handle));

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private boolean handle(Message msg) {
        if (msg.what == MSG_PING && msg.replyTo != null) {
            try {
                Message reply = Message.obtain(null, MSG_PING);
                Bundle b = new Bundle();
                b.putString("result", "uid=0 root service ready");
                reply.setData(b);
                msg.replyTo.send(reply);
            } catch (Exception ignored) {
            }
            return true;
        }
        return false;
    }
}
