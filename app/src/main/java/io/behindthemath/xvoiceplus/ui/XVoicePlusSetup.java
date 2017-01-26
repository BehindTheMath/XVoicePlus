package io.behindthemath.xvoiceplus.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import io.behindthemath.xvoiceplus.receivers.UserPollReceiver;

public class XVoicePlusSetup extends Activity implements OnSharedPreferenceChangeListener {

    private final XVoicePlusFragment mVPFragment = new XVoicePlusFragment();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, mVPFragment)
            .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceScreen preferenceScreen = mVPFragment.getPreferenceScreen();
        if (preferenceScreen != null) {
            SharedPreferences sharedPreferences = preferenceScreen.getSharedPreferences();
            if (sharedPreferences != null) {
                sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceScreen preferenceScreen = mVPFragment.getPreferenceScreen();
        if (preferenceScreen != null) {
            SharedPreferences sharedPreferences = preferenceScreen.getSharedPreferences();
            if (sharedPreferences != null) {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mVPFragment.updateSummary(key);
        if (key.equals("settings_polling_frequency")) {
            UserPollReceiver.startAlarmManager(this);
        }
    }
}
