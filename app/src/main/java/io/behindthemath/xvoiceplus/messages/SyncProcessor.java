package io.behindthemath.xvoiceplus.messages;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import io.behindthemath.xvoiceplus.SharedPrefsManager;
import io.behindthemath.xvoiceplus.gv.GoogleVoiceManager;
import io.behindthemath.xvoiceplus.gv.GvResponse;

import static io.behindthemath.xvoiceplus.messages.SmsUtils.PROVIDER_INCOMING_SMS;
import static io.behindthemath.xvoiceplus.messages.SmsUtils.PROVIDER_OUTGOING_SMS;
import static io.behindthemath.xvoiceplus.messages.SmsUtils.URI_RECEIVED;
import static io.behindthemath.xvoiceplus.messages.SmsUtils.URI_SENT;
import static io.behindthemath.xvoiceplus.messages.SmsUtils.VOICE_INCOMING_SMS;
import static io.behindthemath.xvoiceplus.messages.SmsUtils.VOICE_OUTGOING_SMS;

/**
 * Created by BehindTheMath on 3/23/2017.
 */

public class SyncProcessor {
    private static final String TAG = SyncProcessor.class.getSimpleName();

    public static void startSync(Context context, GoogleVoiceManager googleVoiceManager, SharedPrefsManager sharedPrefsManager) {
        Log.d(TAG, "Starting refresh");
        try {
            syncMessages(context, googleVoiceManager, sharedPrefsManager);
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing messages", e);
        }
    }

    private static void syncMessages(Context context, GoogleVoiceManager googleVoiceManager, SharedPrefsManager sharedPrefsManager) throws Exception {
        Log.d(TAG, "Updating messages");
        List<GvResponse.Conversation> conversations = googleVoiceManager.retrieveMessages();

        long timestamp = sharedPrefsManager.getAppSettings().getLong("timestamp", 0);
        LinkedList<GvResponse.Message> newMessages = new LinkedList<>();
        for (GvResponse.Conversation conversation: conversations) {
            for (GvResponse.Message message : conversation.messages) {
                if(message.phoneNumber != null && message.message != null) {
                    markReadIfNeeded(context, googleVoiceManager, message);
                    if (message.date > timestamp) {
                        newMessages.add(message);
                    }
                }
            }
        }

        Log.d(TAG, "New message count: " + newMessages.size());

        // Sort by date order so the events get added in the same order
        Collections.sort(newMessages, new Comparator<GvResponse.Message>() {
            @Override
            public int compare(GvResponse.Message lhs, GvResponse.Message rhs) {
                return Long.valueOf(lhs.date).compareTo(rhs.date);
            }
        });

        long max = timestamp;
        for (GvResponse.Message message : newMessages) {
            max = Math.max(max, message.date);

            // On first sync, just populate the MMS provider, don't send any broadcasts.
            if (timestamp == 0) {
                insertMessage(context, sharedPrefsManager, message);
                continue;
            }

            // Sync up outgoing messages
            if (message.type == VOICE_OUTGOING_SMS) {
                if (!sharedPrefsManager.removeMessageFromRecentList(message.message)) {
                    Log.d(TAG, "Outgoing message not found in recents, inserting into SMS database.");
                    insertMessage(context, sharedPrefsManager, message);
                }
            } else if (message.type == VOICE_INCOMING_SMS) {
                Set<String> recentPushMessages = sharedPrefsManager.getRecentMessages().getStringSet("push_messages", new HashSet<String>());
                if (recentPushMessages.remove(message.id)) {
                    // We already synthesized this message
                    Log.d(TAG, "Message " + message.id + " was already pushed.");
                    sharedPrefsManager.getRecentMessages().edit().putStringSet("push_messages", recentPushMessages).apply();
                } else {
                    IncomingMessageProcessor.synthesizeMessage(context, sharedPrefsManager, message);
                }
            }
        }
        sharedPrefsManager.getAppSettings().edit().putLong("timestamp", max).apply();
    }

    private static void markReadIfNeeded(Context context, GoogleVoiceManager googleVoiceManager, GvResponse.Message message){
        if (message.read == 0){
            Uri uri;
            if (message.type == VOICE_INCOMING_SMS) {
                uri = URI_RECEIVED;
            } else if (message.type == VOICE_OUTGOING_SMS) {
                uri = URI_SENT;
            } else {
                return;
            }

            Cursor c = context.getContentResolver().query(uri, null, "date = ? AND body = ?",
                    new String[] { String.valueOf(message.date), message.message }, null);
            try {
                if(c != null && c.moveToFirst()){
                    googleVoiceManager.markGvMessageRead(message.id, 1);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error marking message as read. ID: " + message.id, e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
    }

    /**
     * Insert a message into the sms/mms provider. We do this in the case of outgoing messages that
     * were not sent via this phone, and also on initial message sync.
     */
    private static void insertMessage(Context context, SharedPrefsManager sharedPrefsManager, GvResponse.Message message) {
        Uri uri;
        int type;
        if (message.type == VOICE_INCOMING_SMS) {
            uri = URI_RECEIVED;
            type = PROVIDER_INCOMING_SMS;
            message.message = IncomingMessageProcessor.messageWithPrefixSuffix(sharedPrefsManager, message.message);
        } else if (message.type == VOICE_OUTGOING_SMS) {
            uri = URI_SENT;
            type = PROVIDER_OUTGOING_SMS;
        } else {
            return;
        }

        if (!SharedPrefsManager.messageExists(context, message, uri)) {
            ContentValues values = new ContentValues();
            values.put("address", message.phoneNumber);
            values.put("body", message.message);
            values.put("type", type);
            values.put("date", message.date);
            values.put("date_sent", message.date);
            values.put("read", message.read);
            context.getContentResolver().insert(uri, values);
        }
    }
}
