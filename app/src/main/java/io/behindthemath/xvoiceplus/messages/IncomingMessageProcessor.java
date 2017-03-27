package io.behindthemath.xvoiceplus.messages;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.util.Locale;

import io.behindthemath.xvoiceplus.SharedPrefsManager;
import io.behindthemath.xvoiceplus.gv.GoogleVoiceManager;
import io.behindthemath.xvoiceplus.gv.GvResponse;

import static io.behindthemath.xvoiceplus.messages.SmsUtils.FORMAT_3GPP;
import static io.behindthemath.xvoiceplus.messages.SmsUtils.URI_RECEIVED;
import static io.behindthemath.xvoiceplus.messages.SmsUtils.VOICE_INCOMING_SMS;
import static io.behindthemath.xvoiceplus.gv.GoogleVoiceManager.MARK_MESSAGE_READ;


/**
 * Created by BehindTheMath on 3/23/2017.
 */

public class IncomingMessageProcessor {
    private static final String TAG = IncomingMessageProcessor.class.getSimpleName();

    public static void handleIncomingMessage(Context context, GoogleVoiceManager googleVoiceManager, SharedPrefsManager sharedPrefsManager, Intent intent) {
        Bundle extras = intent.getExtras();
        GvResponse.Message message = new GvResponse.Message();

        message.conversationId = extras.getString("conversation_id");
        message.id = extras.getString("call_id");
        message.type = VOICE_INCOMING_SMS;
        message.message = extras.getString("call_content");
        message.phoneNumber = extras.getString("sender_address");
        message.date = Long.valueOf(extras.getString("call_time"));

        sharedPrefsManager.getAppSettings().edit().putLong("timestamp", message.date).apply();

        synthesizeMessage(context, sharedPrefsManager, message);

        try {
            googleVoiceManager.markGvMessageRead(message.id, MARK_MESSAGE_READ);
        } catch (Exception e) {
            Log.w(TAG, "Error marking message as read. ID: " + message.id, e);
        }
    }

    static void synthesizeMessage(Context context, SharedPrefsManager sharedPrefsManager, GvResponse.Message message) {
        if (!SmsUtils.messageExists(context, message, URI_RECEIVED)) {
            try {
                byte[] pdu = SmsUtils.createFakeSms(message.phoneNumber, messageWithPrefixSuffix(sharedPrefsManager, message.message), message.date);
                broadcastMessage(context, pdu);
            } catch (IOException e) {
                Log.e(TAG, "IOException when creating fake SMS, ignoring.", e);
            }
        }
    }

    static String messageWithPrefixSuffix(SharedPrefsManager sharedPrefsManager, String message) {
        SharedPreferences settings = sharedPrefsManager.getSettings();
        return String.format(Locale.getDefault(), "%s%s%s",
                settings.getString("settings_incoming_prefix", ""),
                message,
                settings.getString("settings_incoming_suffix", ""));
    }

    /**
     * Send the systemwide SMS_RECEIVED_ACTION broadcast intent with the new incoming message.
     *
     * Starting with KK, there are 2 broadcast intents sent for each message. The SMS_DELIVER_ACTION
     * intent is sent only to the default messaging app, and only this app can write to the SMS
     * Provider. The SMS_RECEIVED_ACTION broadcast intent is sent systemwide, and can be received
     * by any app, to be notified that there is an incoming SMS.
     *
     * @param context
     * @param pdu
     */
    private static void broadcastMessage(Context context, byte[] pdu) {
        Log.d(TAG, "Creating fake SMS. Broadcasting...");
        //Log.d(TAG, "Broadcasting pdu " + bytesToHex(pdu));

        String deliver_action = "android.provider.Telephony.SMS_DELIVER";
        Intent intent = new Intent()
                .setAction(deliver_action)
                .setFlags(0)
                .putExtra("pdus", new Object[] { pdu })
                .putExtra("format", FORMAT_3GPP);
        context.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS");

        String received_action = "android.provider.Telephony.SMS_RECEIVED";
        intent = new Intent()
                .setAction(received_action)
                .setFlags(0)
                .putExtra("pdus", new Object[] { pdu })
                .putExtra("format", FORMAT_3GPP);
        context.sendOrderedBroadcast(intent, "android.permission.RECEIVE_SMS");
    }
}
