package com.runnirr.xvoiceplus.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceScreen;

import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;
import com.runnirr.xvoiceplus.receivers.UserPollReceiver;

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
        PreferenceScreen ps = mVPFragment.getPreferenceScreen();
        if (ps != null) {
            SharedPreferences sp = ps.getSharedPreferences();
            if (sp != null) {
                sp.registerOnSharedPreferenceChangeListener(this);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceScreen ps = mVPFragment.getPreferenceScreen();
        if (ps != null) {
            SharedPreferences sp = ps.getSharedPreferences();
            if (sp != null) {
                sp.unregisterOnSharedPreferenceChangeListener(this);
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
