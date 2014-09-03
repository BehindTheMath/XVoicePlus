package com.runnirr.xvoiceplus.hooks;

import android.app.AndroidAppHelper;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.runnirr.xvoiceplus.XVoicePlus;
import com.runnirr.xvoiceplus.XVoicePlusService;
import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;
import de.robv.android.xposed.XC_MethodHook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import static de.robv.android.xposed.XposedHelpers.*;

/**
 * Created by Justin Baldwin on 8/31/2014.
 */
public class XSmsMethodHook extends XC_MethodHook {
    private final XVoicePlus xVoicePlus;
    private final String TAG = XVoicePlus.TAG;

    public XSmsMethodHook(XVoicePlus xVoicePlus) {
        this.xVoicePlus = xVoicePlus;
    }

    @Override
    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
        Log.d(TAG, String.format("Hooked %s.%s", param.thisObject.getClass().getCanonicalName(), param.method.getName()));
        if (xVoicePlus.isEnabled()) {
            Log.d(TAG, "Sending via google voice");
            attemptSendViaGoogleVoice(param);
            // If we get here the user wants to use GV so stop.
            param.setResult(null);
        } else {
            Log.d(TAG, "Sending via carrier based on settings");
        }
    }

    @SuppressWarnings("unchecked")
    private void attemptSendViaGoogleVoice(final MethodHookParam param) {
        String destAddr;
        String scAddr;
        ArrayList<String> texts;
        ArrayList<PendingIntent> sentIntents;
        ArrayList<PendingIntent> deliveryIntents;

        destAddr = (String)param.args[0];
        scAddr = (String) param.args[1];

        if (param.args[2] instanceof String) {
            texts = new ArrayList<String>(Collections.singletonList((String) param.args[2]));
        } else {
            texts = (ArrayList<String>) param.args[2];
        }

        if (param.args[3] instanceof PendingIntent) {
            sentIntents = new ArrayList<PendingIntent>(Collections.singletonList((PendingIntent) param.args[3]));
        } else {
            sentIntents = (ArrayList<PendingIntent>) param.args[3];
        }

        if (param.args[4] instanceof PendingIntent) {
            deliveryIntents = new ArrayList<PendingIntent>(Collections.singletonList((PendingIntent) param.args[4]));
        } else {
            deliveryIntents = (ArrayList<PendingIntent>) param.args[4];
        }

        try {
            if (sendText(destAddr, scAddr, texts, sentIntents, deliveryIntents)) {
                Log.i(TAG, "Intent for message to be sent via GV successful");
            } else {
                Log.i(TAG, "Send text failed.");
                // If it fails, fail the message
                XVoicePlusService.fail(sentIntents);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error attempting to send message via Google Voice", e);
            XVoicePlusService.fail(sentIntents);
        }
    }

    private boolean sendText(String destAddr, String scAddr, ArrayList<String> texts,
                             final ArrayList<PendingIntent> sentIntents, final ArrayList<PendingIntent> deliveryIntents) throws IOException {

        Context context = getContext();
        if (context != null) {
            Intent outgoingSms = new Intent()
                    .setAction(MessageEventReceiver.OUTGOING_SMS)
                    .putExtra("destAddr", destAddr)
                    .putExtra("scAddr", scAddr)
                    .putStringArrayListExtra("parts", texts)
                    .putParcelableArrayListExtra("sentIntents", sentIntents)
                    .putParcelableArrayListExtra("deliveryIntents", deliveryIntents);

            context.sendOrderedBroadcast(outgoingSms, null);
            return true;

        } else {
            Log.e(TAG, "Unable to find a context to send the outgoingSms intent");
            return false;
        }
    }

    private Context getContext(){
        // Try to get a context in one way or another from system
        Context context;

        // Seems to work for 4.4
        Log.i(TAG, "Trying to get context from AndroidAppHelper");
        context = AndroidAppHelper.currentApplication();

        // Seems to work for 4.2
        if (context == null) {
            Log.i(TAG, "Trying to get context from mSystemContext");
            Object systemContext = getStaticObjectField(findClass("android.app.ActivityThread", null), "mSystemContext");
            if (systemContext != null) {
                context = (Context) systemContext;
            }
        }

        // Seems to work for 4.1 and 4.0
        if (context == null) {
            Log.i(TAG, "Trying to get activityThread from systemMain");
            Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "systemMain");
            if (activityThread != null){
                Log.i(TAG, "Trying to get context from getSystemContext");
                context = (Context) callMethod(activityThread, "getSystemContext");
            }
        }

        return context;
    }
}
