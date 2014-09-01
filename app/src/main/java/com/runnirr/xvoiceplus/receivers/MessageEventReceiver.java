package com.runnirr.xvoiceplus.receivers;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.runnirr.xvoiceplus.XVoicePlusService;

public class MessageEventReceiver extends XVoicePlusReceiver {
    private static final String TAG = MessageEventReceiver.class.getName();

    public static final String INCOMING_VOICE = "com.runnirr.xvoiceplus.INCOMING_VOICE";
    public static final String OUTGOING_SMS = "com.runnirr.xvoiceplus.OUTGOING_SMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isEnabled(context)) {
            Log.d(TAG, "Received intent for " + intent.getAction());
            intent.setClass(context, XVoicePlusService.class);
            startWakefulService(context, intent);
        }
    }
}
