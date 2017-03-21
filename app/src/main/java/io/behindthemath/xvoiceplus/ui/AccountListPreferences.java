package io.behindthemath.xvoiceplus.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import io.behindthemath.xvoiceplus.gv.GoogleVoiceManager;

public class AccountListPreferences extends ListPreference {

    private static final String TAG = AccountListPreferences.class.getSimpleName();

    public AccountListPreferences(Context context, AttributeSet attrs) {
        super(context, attrs);

        AccountManager accountManager = AccountManager.get(context);
        if (accountManager != null) {
            final Account[] accounts = accountManager.getAccountsByType("com.google");
            String[] entries = new String[accounts.length];
            for (int i = 0; i < accounts.length; i++) {
                entries[i] = accounts[i].name;
            }
            setEntries(entries);
            setEntryValues(entries);

            setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    SharedPreferences prefs = getSharedPreferences();
                    if (prefs != null) {
                        final String previousAccount = prefs.getString("account", null);
                        final String newAccountString = (String) newValue;

                        // If this is the same account, just ignore it
                        if (newValue.equals(previousAccount)) return false;

                        Log.d(TAG, "Account changed to " + newAccountString);
                        for (Account account : accounts) {
                            if (account.name.equals(newAccountString)) {
                                GoogleVoiceManager.invalidateToken(getContext(), previousAccount);

                                GoogleVoiceManager.getToken(getContext(), account);
                                /*
                                 * We can't get the new user_hash from GV SharedPrefs here, so we'll
                                 * wait for the next incoming message, and then use XSharedPreferences
                                 * to get it. Right now we'll just clear the old one.
                                 */
                                prefs.edit().putString("user_hash", null).apply();

                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }
    }
}
