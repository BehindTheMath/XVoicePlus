package com.runnirr.xvoiceplus.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.*;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.runnirr.xvoiceplus.R;
import com.runnirr.xvoiceplus.XVoicePlusService;

public class XVoicePlusFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        Activity activity = getActivity();
        if (activity != null) {
            Log.d("XVoicePlusPlusFragment", "Starting XVoicePlusPlusService...");
            activity.startService(new Intent(activity, XVoicePlusService.class));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View result = super.onCreateView(inflater, container, savedInstanceState);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            String key = preferenceScreen.getPreference(i).getKey();
            updateSummary(key);
        }

        return result;
    }

    /**
     * Updates the summaries of the Preferences in the top section, based on the options selected.
     * @param key
     */
    public void updateSummary(String key) {
        Preference pref = findPreference(key);

        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            listPref.setSummary(listPref.getEntry());
        } else if (pref instanceof EditTextPreference) {
            EditTextPreference textPref = (EditTextPreference) pref;
            textPref.setSummary(textPref.getText());
        }
    }
}
