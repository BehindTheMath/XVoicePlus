package com.runnirr.xvoiceplus.gv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;
import com.runnirr.xvoiceplus.XVoicePlusService;
import com.runnirr.xvoiceplus.gv.GvResponse.Conversation;
import com.runnirr.xvoiceplus.gv.GvResponse.Payload;

public class GoogleVoiceManager {
    private static final String TAG = GoogleVoiceManager.class.getName();

    public static final String ACCOUNT_CHANGED = "com.runnirr.xvoiceplus.ACCOUNT_CHANGED";

    private final Context mContext;
    private String mRnrse = null;
    
    public GoogleVoiceManager(Context context) {
        mContext = context;
    }

    private SharedPreferences getSettings() {
        return mContext.getSharedPreferences("com.runnirr.xvoiceplus_preferences", Context.MODE_WORLD_READABLE);
    }

    private String getAccount() {
        return getSettings().getString("account", null);
    }
    
    public boolean refreshAuth() {
        return getRnrse(true) != null;
    }
    
    private void saveRnrse(String rnrse) {
        getSettings().edit().putString("_rnr_se", rnrse).apply();
    }
    
    private String getRnrse() {
        return getRnrse(false);
    }

    private String getRnrse(boolean force) {
        if (force || mRnrse == null) {
            try {
                mRnrse = fetchRnrSe();
            } catch (Exception e) {
                mRnrse = null;
            }
        }
        return mRnrse;
    }
    
    // fetch the weirdo opaque token google voice needs...
    private String fetchRnrSe() throws Exception {
        final String authToken = getAuthToken();
        JsonObject userInfo = Ion.with(mContext, "https://www.google.com/voice/request/user")
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .asJsonObject()
                .get();

        String rnrse = userInfo.get("r").getAsString();
        verifySmsForwarding(userInfo, authToken, rnrse);

        saveRnrse(rnrse);
        return rnrse;
    }

    private void verifySmsForwarding(JsonObject userInfo, String authToken, String rnrse) {
        try {
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            String number = tm.getLine1Number();
            if (number != null) {
                JsonObject phones = userInfo.getAsJsonObject("phones");
                for (Map.Entry<String, JsonElement> entry: phones.entrySet()) {
                    JsonObject phone = entry.getValue().getAsJsonObject();
                    if (!phone.get("smsEnabled").getAsBoolean()) {
                        break;
                    }
                    if (PhoneNumberUtils.compare(number, phone.get("phoneNumber").getAsString())) {
                        Log.i(TAG, "Disabling SMS forwarding to phone.");
                        Ion.with(mContext, "https://www.google.com/voice/settings/editForwardingSms/")
                        .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                        .setBodyParameter("phoneId", entry.getKey())
                        .setBodyParameter("enabled", "0")
                        .setBodyParameter("_rnr_se", rnrse)
                        .asJsonObject();
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Error verifying GV SMS forwarding", e);
        }
    }

    private static Bundle getAccountBundle(Context context, String account) throws Exception {
        AccountManager am = AccountManager.get(context);
        if (am != null) {
            return am.getAuthToken(new Account(account, "com.google"), "grandcentral", null, true, null, null)
                    .getResult();
        }
        return null;
    }
    
    private String getAuthToken() throws Exception {
        Bundle bundle = getAccountBundle(mContext, getAccount());
        return bundle.getString(AccountManager.KEY_AUTHTOKEN);
    }
    
    // hit the google voice api to send a text
    public void sendGvMessage(String number, String text) throws Exception {
        final String authToken = getAuthToken();
        JsonObject json = Ion.with(mContext, "https://www.google.com/voice/sms/send/")
                .onHeaders(new GvHeadersCallback(mContext, authToken))
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .setBodyParameter("phoneNumber", number)
                .setBodyParameter("sendErrorSms", "0")
                .setBodyParameter("text", text)
                .setBodyParameter("_rnr_se", getRnrse())
                .asJsonObject()
                .get();

        if (!json.get("ok").getAsBoolean())
            throw new Exception(json.toString());
    }

    /**
     * Update the read state on GV
     * @param id - GV message id
     * @param read - 0 = unread, 1 = read
     * @throws Exception
     */
    public void markGvMessageRead(String id, int read) throws Exception {
        final String authToken = getAuthToken();
        Log.d(TAG, "Marking messsage " + id + " as read");
        Ion.with(mContext, "https://www.google.com/voice/inbox/mark/")
        .onHeaders(new GvHeadersCallback(mContext, authToken))
        .setHeader("Authorization", "GoogleLogin auth=" + authToken)
        .setBodyParameter("messages", id)
        .setBodyParameter("read", String.valueOf(read))
        .setBodyParameter("_rnr_se", getRnrse());
    }
    
    // refresh the messages that were on the server
    public List<Conversation> retrieveMessages() throws Exception {
        String account = getAccount();
        Log.d(TAG, "Retrieving messages for account " + account);
        if (account == null) {
            return new ArrayList<Conversation>();
        }

        Log.i(TAG, "Refreshing messages");

        // tokens!
        final String authToken = getAuthToken();

        Payload payload = Ion.with(mContext, "https://www.google.com/voice/request/messages")
                .onHeaders(new GvHeadersCallback(mContext, authToken))
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .as(Payload.class)
                .get();

        return payload.conversations;
    }
    
    public static void invalidateToken(final Context context, final String account) {
        if (account == null)
            return;

        new Thread() {
            @Override
            public void run() {
                try {
                    // grab the auth token
                    Bundle bundle = getAccountBundle(context, account);
                    String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    AccountManager am = AccountManager.get(context);
                    if (am != null) {
                        am.invalidateAuthToken("com.google", authToken);
                        Log.i(TAG, "Token invalidated.");
                    } else {
                        throw new Exception("No account manager found");
                    }
                }
                catch (Exception e) {
                    Log.e(TAG, "error invalidating token", e);
                }
            }
        }.start();
    }

    public static void getToken(final Context context, final Account account) {
        AccountManager am = AccountManager.get(context);
        if (am == null)
            return;

        am.getAuthToken(account, "grandcentral", null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Intent intent = new Intent(context, XVoicePlusService.class);
                    intent.setAction(ACCOUNT_CHANGED);
                    context.startService(intent);

                    Log.i(TAG, "Token retrieved.");
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, new Handler());
    }
    
    
}
