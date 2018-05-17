package io.behindthemath.xvoiceplus.gv;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.koushikdutta.ion.Ion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.behindthemath.xvoiceplus.XVoicePlusService;
import io.behindthemath.xvoiceplus.gv.GvResponse.Conversation;
import io.behindthemath.xvoiceplus.gv.GvResponse.Payload;

public class GoogleVoiceManager {
    private static final String TAG = GoogleVoiceManager.class.getSimpleName();

    public static final String ACCOUNT_CHANGED = "io.behindthemath.xvoiceplus.ACCOUNT_CHANGED";

    public static final int MARK_MESSAGE_UNREAD = 0;
    public static final int MARK_MESSAGE_READ = 1;

    private final Context mContext;
    private String mRnrse = null;

    public GoogleVoiceManager(Context context) {
        mContext = context;
    }

    private SharedPreferences getSettings() {
        return mContext.getSharedPreferences("io.behindthemath.xvoiceplus_preferences", Context.MODE_PRIVATE);
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

    /**
     * Fetch the weirdo opaque token Google Voice needs...
     *
     * @return
     *
     * @throws Exception
     */
    private String fetchRnrSe() throws Exception {
        final String authToken = getAuthToken();
        JsonObject userInfo = Ion.with(mContext).load("https://www.google.com/voice/request/user")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36")
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .asJsonObject()
                .get();

        Log.d(TAG, "fetchRnrSe: "+userInfo.getAsString());

        String rnrse = userInfo.get("r").getAsString();
        verifySmsForwarding(userInfo, authToken, rnrse);

        saveRnrse(rnrse);
        return rnrse;
    }

    private void verifySmsForwarding(JsonObject userInfo, String authToken, String rnrse) {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            String number = telephonyManager.getLine1Number();
            if (number != null) {
                JsonObject phones = userInfo.getAsJsonObject("phones");
                for (Map.Entry<String, JsonElement> entry: phones.entrySet()) {
                    JsonObject phone = entry.getValue().getAsJsonObject();
                    if (!phone.get("smsEnabled").getAsBoolean()) {
                        break;
                    }
                    if (PhoneNumberUtils.compare(number, phone.get("phoneNumber").getAsString())) {
                        Log.i(TAG, "Disabling SMS forwarding to phone.");
                        Ion.with(mContext).load("https://www.google.com/voice/settings/editForwardingSms/")
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36")
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
        AccountManager accountManager = AccountManager.get(context);
        if (accountManager != null) {
            return accountManager.getAuthToken(new Account(account, "com.google"), "grandcentral", null, true, null, null)
                    .getResult();
        }
        return null;
    }

    private String getAuthToken() throws Exception {
        Bundle bundle = getAccountBundle(mContext, getAccount());
        return bundle.getString(AccountManager.KEY_AUTHTOKEN);
    }

    /**
     * Hit the Google Voice API to send a text
     *
     * @param number
     * @param text
     *
     * @throws Exception
     */
    public void sendGvMessage(String number, String text) throws Exception {
        final String authToken = getAuthToken();
        String response = Ion.with(mContext).load("https://www.google.com/voice/sms/send/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36")
                .onHeaders(new GvHeadersCallback(mContext, authToken))
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .setBodyParameter("phoneNumber", number)
                .setBodyParameter("sendErrorSms", "0")
                .setBodyParameter("text", text)
                .setBodyParameter("_rnr_se", getRnrse())
                .asString()
                .get();
        try {
            boolean isOk = new JsonParser().parse(response).getAsJsonObject().get("ok").getAsBoolean();
            if (!isOk){
                throw new Exception(response);
            }
        } catch (JsonParseException e){
            throw new Exception(response);
        }
        Log.d(TAG, "Message sent with Google Voice successfully");
    }

    /**
     * Update the read state on GV
     *
     * @param id - GV message id
     * @param read - 0 = unread, 1 = read
     *
     * @throws Exception
     */
    public void markGvMessageRead(String id, int read) throws Exception {
        final String authToken = getAuthToken();
        Log.d(TAG, "Marking messsage " + id + " as read");
        Ion.with(mContext).load("https://www.google.com/voice/inbox/mark/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36")
                .onHeaders(new GvHeadersCallback(mContext, authToken))
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .setBodyParameter("messages", id)
                .setBodyParameter("read", String.valueOf(read))
                .setBodyParameter("_rnr_se", getRnrse());
    }

    /**
     * Refresh the messages that were on the server
     *
     * @return
     *
     * @throws Exception
     */
    public List<Conversation> retrieveMessages() throws Exception {
        String account = getAccount();
        if (account == null) {
            Log.d(TAG, "Account not set");
            return new ArrayList<>();
        }

        Log.i(TAG, "Refreshing messages");

        // tokens!
        final String authToken = getAuthToken();
        try {
            return Ion.with(mContext).load("https://www.google.com/voice/request/messages")
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36")
                    .onHeaders(new GvHeadersCallback(mContext, authToken))
                    .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                    .as(Payload.class).get().conversations;

        } catch (Throwable e){
            Log.e(TAG, "Unable to retrieve messages: "+e.getMessage(), e);
            throw e;
        }
    }

    public static void invalidateToken(final Context context, final String account) {
        if (account == null) return;

        new Thread() {
            @Override
            public void run() {
                try {
                    // Grab the auth token
                    Bundle bundle = getAccountBundle(context, account);
                    String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    AccountManager accountManager = AccountManager.get(context);
                    if (accountManager != null) {
                        accountManager.invalidateAuthToken("com.google", authToken);
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
        AccountManager accountManager = AccountManager.get(context);
        if (accountManager == null) return;

        accountManager.getAuthToken(account, "grandcentral", null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Intent intent = new Intent(context, XVoicePlusService.class);
                    intent.setAction(ACCOUNT_CHANGED);
                    context.startService(intent);

                    Log.i(TAG, "Token retrieved.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, new Handler());
    }
}
