package io.behindthemath.xvoiceplus.messages;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.behindthemath.xvoiceplus.SharedPrefsManager;
import io.behindthemath.xvoiceplus.gv.GoogleVoiceManager;

import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;

/**
 * Created by BehindTheMath on 3/23/2017.
 */

public class OutgoingMessageProcessor {
    private static final String TAG = OutgoingMessageProcessor.class.getSimpleName();

    /**
     *  Deserialize the intent extras from io.behindthemath.xvoiceplus.OUTGOING_SMS
     */
    public static void handleOutgoingSms(GoogleVoiceManager googleVoiceManager, SharedPrefsManager sharedPrefsManager, Intent intent) {
        String destAddr = intent.getStringExtra("destAddr");
        ArrayList<String> parts = intent.getStringArrayListExtra("parts");
        ArrayList<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");

        // Combine the multipart text into one string
        StringBuilder textBuilder = new StringBuilder();
        for (String text: parts) {
            textBuilder.append(text);
        }
        String text = textBuilder.toString();

        try {
            // Send it off, and note that we recently sent this message for round trip tracking
            sendGvMessage(googleVoiceManager, sharedPrefsManager, destAddr, text, sentIntents);
            return;
        } catch (Exception e) {
            Log.e(TAG, "Error sending message", e);
        }

        try {
            // On failure, fetch info and try again
            googleVoiceManager.refreshAuth();
            sendGvMessage(googleVoiceManager, sharedPrefsManager, destAddr, text, sentIntents);
        } catch (Exception e) {
            Log.e(TAG, "Send failure", e);
            fail(sentIntents);
        }
    }

    private static void sendGvMessage(GoogleVoiceManager googleVoiceManager, SharedPrefsManager sharedPrefsManager,
                               String destAddr, String text, ArrayList<PendingIntent> sentIntents) throws Exception {
        googleVoiceManager.sendGvMessage(destAddr, text);
        if (sharedPrefsManager.syncEnabled()) sharedPrefsManager.addMessageToRecentList(text);
        success(sentIntents);
    }

    /** Marks all sent intents as failures.
     *
     * @param sentIntents A list of {@link PendingIntent}s to broadcast when the message is sent or not.
     */
    public static void fail(List<PendingIntent> sentIntents) {
        if (sentIntents == null) return;

        for (PendingIntent sentIntent: sentIntents) {
            if (sentIntent != null){
                try {
                    // Based on com.android.internal.telephony.SMSDispatcher$SmsTracker#handleSendComplete()
                    sentIntent.send(RESULT_ERROR_GENERIC_FAILURE);
                } catch (Exception e) {
                    Log.e(TAG, "Error marking failed intent", e);
                }
            }
        }
    }

    /**
     * Marks all sent intents as successfully sent.
     *
     * @param sentIntents A list of {@link PendingIntent}s to broadcast when the message is sent.
     */
    public static void success(List<PendingIntent> sentIntents) {
        if (sentIntents == null) return;

        for (PendingIntent sentIntent: sentIntents) {
            if (sentIntent != null) {
                try {
                    // Success code is based on com.android.internal.telephony.SMSDispatcher$SmsTracker#onSent()
                    sentIntent.send(Activity.RESULT_OK);
                } catch (Exception e) {
                    Log.e(TAG, "Error marking success intent", e);
                }
            }
        }
    }
}
