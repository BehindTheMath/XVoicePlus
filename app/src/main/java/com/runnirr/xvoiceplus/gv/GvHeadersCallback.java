package com.runnirr.xvoiceplus.gv;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.ion.HeadersCallback;

public class GvHeadersCallback implements HeadersCallback {
    
    private static final String TAG = GvHeadersCallback.class.getName();
    
    private final String mAuthToken;
    private final Context mContext;
    
    public GvHeadersCallback(Context context, String authToken) {
        super();
        mAuthToken = authToken;
        mContext = context;
    }

    @Override
    public void onHeaders(RawHeaders headers) {
        if (headers.getResponseCode() == 401) {
            Log.e(TAG, "Refresh failed:\n" + headers.toHeaderString());
            AccountManager am = AccountManager.get(mContext);
            if (am != null) {
                am.invalidateAuthToken("com.google", mAuthToken);
            }
            removeRnrse();
        }
    }

    private void removeRnrse() {
        getSettings().edit().remove("_rnr_se").apply();
    }

    private SharedPreferences getSettings() {
        return mContext.getSharedPreferences("com.runnirr.xvoiceplus_preferences", Context.MODE_WORLD_READABLE);
    }
}
