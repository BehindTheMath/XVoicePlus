package io.behindthemath.xvoiceplus.receivers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.WakefulBroadcastReceiver;

public abstract class XVoicePlusReceiver extends WakefulBroadcastReceiver {

    protected static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences("io.behindthemath.xvoiceplus_preferences", Context.MODE_WORLD_READABLE);
    }

    protected static boolean isEnabled (Context context) {
        return getPreferences(context).getBoolean("settings_enabled", false);
    }

    @Override
    public abstract void onReceive(Context context, Intent intent);
}
