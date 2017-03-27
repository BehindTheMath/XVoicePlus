package io.behindthemath.xvoiceplus;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import io.behindthemath.xvoiceplus.gv.GoogleVoiceManager;
import io.behindthemath.xvoiceplus.messages.IncomingMessageProcessor;
import io.behindthemath.xvoiceplus.messages.OutgoingMessageProcessor;
import io.behindthemath.xvoiceplus.messages.SyncProcessor;
import io.behindthemath.xvoiceplus.receivers.BootCompletedReceiver;
import io.behindthemath.xvoiceplus.receivers.MessageEventReceiver;
import io.behindthemath.xvoiceplus.receivers.UserPollReceiver;

public class XVoicePlusService extends IntentService {
    private static final String TAG = XVoicePlusService.class.getSimpleName();

    private GoogleVoiceManager mGVManager = new GoogleVoiceManager(this);

    public XVoicePlusService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        SharedPrefsManager sharedPrefsManager = new SharedPrefsManager(this);

        if ((!sharedPrefsManager.getSettings().getBoolean("settings_enabled", false)) || intent.getAction() == null) {
            return;
        }

        Log.d(TAG, "Handling intent for action " + intent.getAction());

        // Handle an outgoing SMS
        if (MessageEventReceiver.OUTGOING_SMS.equals(intent.getAction())) {
            OutgoingMessageProcessor.handleOutgoingSms(mGVManager, sharedPrefsManager, intent);
            if (sharedPrefsManager.getSettings().getBoolean("settings_sync_on_send", false)) {
                Log.d(TAG, "Sync on send enabled.");
                SyncProcessor.startSync(this, mGVManager, sharedPrefsManager);
            }
            MessageEventReceiver.completeWakefulIntent(intent);

        // Polling
        } else if (UserPollReceiver.USER_POLL.equals(intent.getAction())) {
            SyncProcessor.startSync(this, mGVManager, sharedPrefsManager);
            UserPollReceiver.completeWakefulIntent(intent);

        // Incoming message
        } else if (MessageEventReceiver.INCOMING_VOICE.equals(intent.getAction())) {
            if (sharedPrefsManager.getSettings().getBoolean("settings_sync_on_receive", false)) {
                Log.d(TAG, "Sync on receive enabled");
                SyncProcessor.startSync(this, mGVManager, sharedPrefsManager);
            } else {
                IncomingMessageProcessor.handleIncomingMessage(this, mGVManager, sharedPrefsManager, intent);
            }
            sharedPrefsManager.clearRecentList();
            MessageEventReceiver.completeWakefulIntent(intent);

        // Boot
        } else if (BootCompletedReceiver.BOOT_COMPLETED.equals(intent.getAction())) {
            if (sharedPrefsManager.getSettings().getBoolean("settings_sync_on_boot", false)) {
                Log.d(TAG, "Sync on boot enabled.");
                SyncProcessor.startSync(this, mGVManager, sharedPrefsManager);
            }
            BootCompletedReceiver.completeWakefulIntent(intent);

        // Google account changed
        } else if (GoogleVoiceManager.ACCOUNT_CHANGED.equals(intent.getAction())) {
            mGVManager = new GoogleVoiceManager(this);
        }
    }
}
