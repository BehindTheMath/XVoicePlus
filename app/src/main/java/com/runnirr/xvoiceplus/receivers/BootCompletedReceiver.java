package com.runnirr.xvoiceplus.receivers;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.runnirr.xvoiceplus.R;
import com.runnirr.xvoiceplus.XVoicePlusService;

public class BootCompletedReceiver extends XVoicePlusReceiver {
    public static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isEnabled(context)) {
            Toast.makeText(context, context.getResources().getString(R.string.xvoiceplus_started), Toast.LENGTH_LONG).show();
            UserPollReceiver.startAlarmManager(context);
            intent.setClass(context, XVoicePlusService.class);
            startWakefulService(context, intent);
        }
    }
}
