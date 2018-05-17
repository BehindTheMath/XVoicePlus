package io.behindthemath.xvoiceplus.hooks;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import com.crossbowffs.remotepreferences.RemotePreferenceAccessException;
import com.crossbowffs.remotepreferences.RemotePreferences;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import io.behindthemath.xvoiceplus.RegisteredAccountsParser;
import io.behindthemath.xvoiceplus.XVoicePlus;
import io.behindthemath.xvoiceplus.receivers.MessageEventReceiver;

import static io.behindthemath.xvoiceplus.XVoicePlus.LEGACY_GOOGLE_VOICE_PACKAGE;
import static io.behindthemath.xvoiceplus.XVoicePlus.XVOICE_PLUS_PACKAGE;
import static io.behindthemath.xvoiceplus.XVoicePlus.XVOICE_PLUS_PREFERENCES_FILE_NAME;

/**
 * Created by BehindTheMath on 2/25/2017.
 */

public class GCMListenerServiceHook extends XC_MethodHook {
    private static final String TAG = GCMListenerServiceHook.class.getSimpleName();

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

        Bundle bundle = (Bundle) param.args[1];
        if (bundle == null || bundle.isEmpty()) return;

        final String userHash = bundle.getString("user_hash");
        // If the GCM message doesn't have sender_address or user_hash, ignore it
        if (bundle.getString("sender_address", null) == null || userHash == null) return;

        if (XVoicePlus.isEnabled()) {
            // Verify that the user_hash of the incoming message matched the user_hash of the account we listen for
            if (!verifyUserHash(userHash)) {
                Log.d(TAG, "user_hash did not match. This message must be for a different account than the one we're listening for, so we'll ignore it");
                return;
            }

            Log.d(TAG, "Received incoming Google Voice message");

            // Send the incoming message to be processed
            Intent intent = new Intent()
                    .setAction(MessageEventReceiver.INCOMING_VOICE)
                    .putExtra("conversation_id", bundle.getString("conversation_id", null))
                    .putExtra("call_id", bundle.getString("call_id", null))
                    .putExtra("call_content", bundle.getString("call_content", null))
                    .putExtra("sender_address", bundle.getString("sender_address", null))
                    .putExtra("call_time", bundle.getString("call_time", null));

            AndroidAppHelper.currentApplication().getApplicationContext().sendOrderedBroadcast(intent, null);
        }
    }

    /**
     * Verifies that the user_hash of the incoming message matches the account we're listening for.
     * If the correct user_hash isn't already stored in our SharedPrefs, store it for future reference.
     *
     * @param userHash The incoming user_hash.
     * @return {@code True} if the user_hash of the incoming message matches the account
     * we're listening for; otherwise, returns {@code false}.
     */
    private boolean verifyUserHash(final String userHash) {
        final String userHashFromSharedPrefs;

        final Context context = AndroidAppHelper.currentApplication().getApplicationContext();
        final SharedPreferences remotePreferences = new RemotePreferences(context, XVOICE_PLUS_PACKAGE, XVOICE_PLUS_PREFERENCES_FILE_NAME, true);

        try {
            userHashFromSharedPrefs = remotePreferences.getString("user_hash", null);
        } catch (RemotePreferenceAccessException e) {
            Log.e(TAG, "Error accessing user_hash from RemotePreferences: ", e);
            return false;
        }

        if (userHashFromSharedPrefs != null) {
            // If we already have the user_hash stored in our SharedPrefs, just compare it to the one in the bundle
            return userHashFromSharedPrefs.equals(userHash);
        } else {
            // ...Otherwise we need to pull it out of the GV SharedPrefs
            final String accountName;

            try {
                accountName = remotePreferences.getString("account", null);
            } catch (RemotePreferenceAccessException e) {
                Log.e(TAG, "Error accessing account name from RemotePreferences: ", e);
                return false;
            }

            if (accountName == null) {
                Log.e(TAG, "Error retrieving account name from SharedPreferences");
                return false;
            }

            final SharedPreferences gvSharedPrefs = new XSharedPreferences(LEGACY_GOOGLE_VOICE_PACKAGE);
            final String registeredAccounts = gvSharedPrefs.getString("accounts", null);
            if (registeredAccounts == null) {
                Log.e(TAG, "Error accessing registered_accounts from GV SharedPreferences");
                return false;
            }
            final byte[] registeredAccountsBytes = Base64.decode(registeredAccounts, Base64.DEFAULT);

            if (!RegisteredAccountsParser.isMatch(accountName, userHash, registeredAccountsBytes)) {
                return false;
            } else {
                // Cache the user_hash in our SharedPrefs, so we don't have to recalculate it in the future
                remotePreferences.edit().putString("user_hash", userHash).apply();
                return true;
            }
        }
    }
}
