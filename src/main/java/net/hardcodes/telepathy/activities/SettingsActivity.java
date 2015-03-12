package net.hardcodes.telepathy.activities;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.model.FontTextView;
import net.hardcodes.telepathy.tools.Utils;

public class SettingsActivity extends Activity implements RadioGroup.OnCheckedChangeListener {
    private static final String[] bitrateOptions = {"Low (256 Kbps)", "Medium (512 Kbps)", "High (1 Mbps)", "Very High (2 Mbps)"};
    private static final String[] bitrateValues = {"0.25", "0.5", "1", "2"};
    FontTextView btnSystem;
    FontTextView btnPermissions;
    FontTextView btnNetwork;
    LinearLayout systemSettings;
    LinearLayout permissionsSettings;
    LinearLayout networkSettings;
    CheckBox checkBoxStartServer;
    CheckBox checkBoxLoginAuto;
    FontTextView arrowWiFiLeft;
    FontTextView arrowWiFiRight;
    FontTextView arrowMobileLeft;
    FontTextView arrowMobileRight;
    FontTextView bitrateWiFiSelected;
    FontTextView bitrateMobileSelected;
    RadioGroup radioGroupConnection;
    RadioGroup radioGroupRemoteControl;
    RadioGroup radioGroupScreen;

    private EditTextPreference portNumberPref;
    private ListPreference bitratePref;
    private ListPreference resolutionPref;
    private int selectedBitRateWiFi = 2;
    private int selectedBitRateMobile = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        radioGroupConnection = (RadioGroup) findViewById(R.id.radio_group_connection);
        radioGroupRemoteControl = (RadioGroup) findViewById(R.id.radio_group_remote_control);
        radioGroupScreen = (RadioGroup) findViewById(R.id.radio_group_screen);
        arrowWiFiLeft = (FontTextView) findViewById(R.id.wifi_arrow_left);
        arrowWiFiRight = (FontTextView) findViewById(R.id.wifi_arrow_right);
        arrowMobileLeft = (FontTextView) findViewById(R.id.mobile_arrow_left);
        arrowMobileRight = (FontTextView) findViewById(R.id.mobile_arrow_right);
        bitrateWiFiSelected = (FontTextView) findViewById(R.id.wifi_selected_item);
        bitrateMobileSelected = (FontTextView) findViewById(R.id.mobile_selected_item);
        Typeface custom_font = Typeface.createFromAsset(getAssets(), "font/forced_square.ttf");

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

        radioGroupConnection.setOnCheckedChangeListener(this);
        radioGroupRemoteControl.setOnCheckedChangeListener(this);
        radioGroupScreen.setOnCheckedChangeListener(this);

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

        arrowWiFiLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedBitRateWiFi > 0) {
                    selectedBitRateWiFi--;
                }
                bitrateWiFiSelected.setText(bitrateOptions[selectedBitRateWiFi]);
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_BITRATE_WIFI, bitrateValues[selectedBitRateWiFi]);
            }
        });
        arrowWiFiRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedBitRateWiFi < 3) {
                    selectedBitRateWiFi++;
                }
                bitrateWiFiSelected.setText(bitrateOptions[selectedBitRateWiFi]);
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_BITRATE_WIFI, bitrateValues[selectedBitRateWiFi]);
            }
        });
        arrowMobileLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedBitRateMobile > 0) {
                    selectedBitRateMobile--;
                }
                bitrateMobileSelected.setText(bitrateOptions[selectedBitRateMobile]);
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_BITRATE_MOBILE, bitrateValues[selectedBitRateMobile]);
            }
        });
        arrowMobileRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedBitRateMobile < 3) {
                    selectedBitRateMobile++;
                }
                bitrateMobileSelected.setText(bitrateOptions[selectedBitRateMobile]);
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_BITRATE_MOBILE, bitrateValues[selectedBitRateMobile]);
            }
        });
    }

    public void onCheckboxClicked(View v) {
        switch (v.getId()) {
            case R.id.checkbox_start_server_boot:
                if (checkBoxStartServer.isChecked()) {
                    Utils.setBooleanPref(SettingsActivity.this, Constants.PREFERENCE_START_SERVER_ON_BOOT, true);
                } else {
                    Utils.setBooleanPref(SettingsActivity.this, Constants.PREFERENCE_START_SERVER_ON_BOOT, false);
                }
                break;

            case R.id.checkbox_login_auto:
                if (checkBoxLoginAuto.isChecked()) {
                    Utils.setBooleanPref(SettingsActivity.this, Constants.PREFERENCE_LOGIN_AUTO, true);
                } else {
                    Utils.setBooleanPref(SettingsActivity.this, Constants.PREFERENCE_LOGIN_AUTO, false);
                }
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.radio_prompt_connection:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_PROMPT);
                break;
            case R.id. radio_allow_connection:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_ALLOW);
                break;
            case R.id.radio_deny_connection:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_DENY);
                break;
            case R.id.radio_prompt_remote_control:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_PROMPT);
                break;
            case R.id. radio_allow_remote_control:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_ALLOW);
                break;
            case R.id.radio_deny_remote_control:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_DENY);
                break;
            case R.id.radio_prompt_screen:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_PROMPT);
                break;
            case R.id. radio_allow_screen:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_ALLOW);
                break;
            case R.id.radio_deny_screen:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_DENY);
                break;
        }
    }
}