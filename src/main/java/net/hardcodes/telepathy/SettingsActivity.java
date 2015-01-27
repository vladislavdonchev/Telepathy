package net.hardcodes.telepathy;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private EditTextPreference portNumberPref;
    private ListPreference bitratePref;
    private ListPreference resolutionPref;

    public static final String KEY_SERVER_PREF = "server";
    public static final String KEY_BITRATE_PREF = "bitrate";
    public static final String KEY_RESOLUTION_PREF = "resolution";

    private static final String[] bitrateOptions = {"256 Kbps", "512 Kbps", "1 Mbps", "2 Mbps"};
    private static final String[] bitrateValues = {"0.25", "0.5", "1", "2"};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        portNumberPref = (EditTextPreference) findPreference(KEY_SERVER_PREF);
        portNumberPref.setSummary("Telepathy server serverAddress.");

        bitratePref = (ListPreference) findPreference(KEY_BITRATE_PREF);
        bitratePref.setEntries(bitrateOptions);
        bitratePref.setEntryValues(bitrateValues);

        resolutionPref = (ListPreference) findPreference(KEY_RESOLUTION_PREF);
        setResolutionOptions();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setResolutionOptions() {
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(point);
        int nativeWidth = point.x;
        int nativeHeight = point.y;
        String[] resolutionOptions = new String[4];
        for (int i = 1; i < 5; ++i) {
            resolutionOptions[i - 1] = String.valueOf(nativeHeight / i) + " x " + String.valueOf(nativeWidth / i);
        }
        resolutionPref.setEntries(resolutionOptions);
        resolutionPref.setEntryValues(new String[]{"1", "0.5", ".33", ".25"});
    }
}
