package io.behindthemath.xvoiceplus.hooks;

import android.app.AndroidAppHelper;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.behindthemath.xvoiceplus.XVoicePlus;
import io.behindthemath.xvoiceplus.XVoicePlusService;
import io.behindthemath.xvoiceplus.receivers.MessageEventReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import de.robv.android.xposed.XC_MethodHook;

/**
 * Created by Justin Baldwin on 8/31/2014.
 */
public class XSendSmsMethodHook extends XC_MethodHook {
    private final String TAG = XSendSmsMethodHook.class.getName();

    public XSendSmsMethodHook() {}

    @Override
    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
        Log.d(TAG, String.format("Hooked %s.%s", param.thisObject.getClass().getName(), param.method.getName()));

        if (XVoicePlus.isEnabled()) {
            Log.d(TAG, "Sending via Google Voice");
            attemptSendViaGoogleVoice(param);
            // If we get here, the user wants to use GV, so stop the system from sending as a regular SMS.
            param.setResult(null);
        } else {
            Log.d(TAG, XVoicePlus.APP_NAME + " is disabled, so sending via carrier as normal");
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

        if ("sendTextMessage".equals(param.method.getName())) {
            texts = new ArrayList<String>(Collections.singletonList((String) param.args[2]));
            sentIntents = new ArrayList<PendingIntent>(Collections.singletonList((PendingIntent) param.args[3]));
            deliveryIntents = new ArrayList<PendingIntent>(Collections.singletonList((PendingIntent) param.args[4]));
        } else if ("sendMultipartTextMessage".equals(param.method.getName())) {
            texts = (ArrayList<String>) param.args[2];
            sentIntents = (ArrayList<PendingIntent>) param.args[3];
            deliveryIntents = (ArrayList<PendingIntent>) param.args[4];
        } else {
            // We should never get here; it's just to get rid of the "variable might not have been initialized" warnings
            return;
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

        Context context = AndroidAppHelper.currentApplication();
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
            Log.e(TAG, "Unable to find a context to send the OUTGOING_SMS intent");
            return false;
        }
    }
}
