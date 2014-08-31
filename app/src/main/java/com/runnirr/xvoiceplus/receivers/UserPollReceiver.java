package com.runnirr.xvoiceplus.receivers;

import com.runnirr.xvoiceplus.R;
import com.runnirr.xvoiceplus.XVoicePlusService;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class UserPollReceiver extends XVoicePlusReceiver {
    private static final String TAG = UserPollReceiver.class.getName();
    public static final String USER_POLL = "com.runnirr.xvoiceplus.USER_POLL";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (isEnabled(context)) {
            intent.setClass(context, XVoicePlusService.class);
            startWakefulService(context, intent);
        }
    }

    private static PendingIntent getUserPollPendingIntent(Context context) {
        Intent intent = new Intent().setAction(USER_POLL);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private static AlarmManager getAlarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public static void startAlarmManager(Context context) {
        if (isEnabled(context)) {
            String defaultPollingFreq = context.getString(R.string.default_polling_frequency);
            String pollingFreqStr = getPreferences(context).getString("settings_polling_frequency", defaultPollingFreq);
            long pollingFreq = Long.valueOf(pollingFreqStr);
            Log.i(TAG, "PollingFreq: " + pollingFreq);

            if (pollingFreq > 0) {
                getAlarmManager(context).setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime(), pollingFreq, getUserPollPendingIntent(context));    
            } else {
                getAlarmManager(context).cancel(getUserPollPendingIntent(context));
            }
        }
    }
}
