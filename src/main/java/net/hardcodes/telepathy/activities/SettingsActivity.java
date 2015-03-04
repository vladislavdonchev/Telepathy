package net.hardcodes.telepathy.activities;

import android.app.Activity;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.model.FontButton;
import net.hardcodes.telepathy.model.FontTextView;

public class SettingsActivity extends Activity {

    public static final String KEY_SERVER_PREF = "server";
    public static final String KEY_BITRATE_PREF = "bitrate";
    public static final String KEY_RESOLUTION_PREF = "resolution";
    private static final String[] bitrateOptions = {"256 Kbps", "512 Kbps", "1 Mbps", "2 Mbps"};
    private static final String[] bitrateValues = {"0.25", "0.5", "1", "2"};
    FontTextView btnSystem;
    FontTextView btnPermissions;
    FontTextView btnNetwork;
    LinearLayout systemSettings;
    LinearLayout permissionsSettings;
    LinearLayout networkSettings;
    private EditTextPreference portNumberPref;
    private ListPreference bitratePref;
    private ListPreference resolutionPref;
    CheckBox checkBoxStartServer;
    CheckBox checkBoxLoginAuto;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Typeface custom_font = Typeface.createFromAsset(getAssets(),"font/forced_square.ttf");

        systemSettings = (LinearLayout) findViewById(R.id.system_layout);
        permissionsSettings = (LinearLayout) findViewById(R.id.permissions_layout);
        networkSettings = (LinearLayout) findViewById(R.id.network_layout);

        btnSystem = (FontTextView) findViewById(R.id.system_textview);
        btnPermissions = (FontTextView) findViewById(R.id.permissions_textview);
        btnNetwork = (FontTextView) findViewById(R.id.network_textview);

        checkBoxStartServer = (CheckBox) findViewById(R.id.checkbox_start_server_boot);
        checkBoxLoginAuto = (CheckBox) findViewById(R.id.checkbox_login_auto);

        checkBoxStartServer.setTypeface(custom_font);
        checkBoxLoginAuto.setTypeface(custom_font);

        btnSystem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                systemSettings.setVisibility(View.VISIBLE);
                permissionsSettings.setVisibility(View.GONE);
                networkSettings.setVisibility(View.GONE);
            }
        });


        btnPermissions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                systemSettings.setVisibility(View.GONE);
                permissionsSettings.setVisibility(View.VISIBLE);
                networkSettings.setVisibility(View.GONE);
            }
        });


        btnNetwork.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                systemSettings.setVisibility(View.GONE);
                permissionsSettings.setVisibility(View.GONE);
                networkSettings.setVisibility(View.VISIBLE);
            }
        });
//        portNumberPref = (EditTextPreference) findPreference(KEY_SERVER_PREF);
//
//        bitratePref = (ListPreference) findPreference(KEY_BITRATE_PREF);
//        bitratePref.setEntries(bitrateOptions);
//        bitratePref.setEntryValues(bitrateValues);
//        bitratePref.setDefaultValue("0.25");

//        resolutionPref = (ListPreference) findPreference(KEY_RESOLUTION_PREF);
        //       setResolutionOptions();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
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
        resolutionPref.setDefaultValue("0.25");
    }
}
