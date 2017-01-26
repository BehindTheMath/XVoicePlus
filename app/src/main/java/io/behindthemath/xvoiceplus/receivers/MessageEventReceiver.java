package io.behindthemath.xvoiceplus.receivers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import io.behindthemath.xvoiceplus.XVoicePlusService;

public class MessageEventReceiver extends XVoicePlusReceiver {
    private static final String TAG = MessageEventReceiver.class.getSimpleName();

    public static final String INCOMING_VOICE = "io.behindthemath.xvoiceplus.INCOMING_VOICE";
    public static final String OUTGOING_SMS = "io.behindthemath.xvoiceplus.OUTGOING_SMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isEnabled(context)) {
            Log.d(TAG, "Received intent for " + intent.getAction());

            abortBroadcast();
            setResultCode(Activity.RESULT_CANCELED);
            intent.setClass(context, XVoicePlusService.class);
            startWakefulService(context, intent);
        }
    }
}
