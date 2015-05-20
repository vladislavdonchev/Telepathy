package net.hardcodes.telepathy.activities;

import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.dialogs.ServerDialog;
import net.hardcodes.telepathy.tools.Logger;
import net.hardcodes.telepathy.tools.Utils;
import net.hardcodes.telepathy.views.FontTextView;

import java.util.Arrays;

public class SettingsActivity extends BaseActivity implements RadioGroup.OnCheckedChangeListener {
    private static final String[] bitrateOptions = {"Low (256 Kbps)", "Medium (512 Kbps)", "High (1 Mbps)", "Very High (2 Mbps)"};
    private static final String[] bitrateValues = {"0.25", "0.5", "1", "2"};

    private ImageView systemSettingsTitle;
    private ImageView permissionSettingsTitle;
    private ImageView networkSettingsTitle;
    private LinearLayout systemSettings;
    private LinearLayout permissionsSettings;
    private LinearLayout networkSettings;
    private CheckBox checkBoxStartServer;
    private CheckBox checkBoxLoginAuto;
    private FontTextView arrowWiFiLeft;
    private FontTextView arrowWiFiRight;
    private FontTextView arrowMobileLeft;
    private FontTextView arrowMobileRight;
    private FontTextView bitrateWiFiSelected;
    private FontTextView bitrateMobileSelected;
    private RadioGroup radioGroupConnection;
    private RadioGroup radioGroupRemoteControl;
    private RadioGroup radioGroupScreen;
    private ImageView settingsTitle;
    private FontTextView systemServiceStatus;
    private FontTextView serverConfiguration;

    private EditTextPreference portNumberPref;
    private ListPreference bitratePref;
    private ListPreference resolutionPref;
    private int selectedBitRateWiFi = 2;
    private int selectedBitRateMobile = 1;

    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContents(R.layout.activity_settings);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

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

        systemSettingsTitle = (ImageView) findViewById(R.id.system_settings_title);
        permissionSettingsTitle = (ImageView) findViewById(R.id.permissions_settings_title);
        networkSettingsTitle = (ImageView) findViewById(R.id.network_settings_title);

        checkBoxStartServer = (CheckBox) findViewById(R.id.checkbox_start_server_boot);
        checkBoxLoginAuto = (CheckBox) findViewById(R.id.checkbox_login_auto);
        settingsTitle = (ImageView) findViewById(R.id.settings_title);
        systemServiceStatus = (FontTextView) findViewById(R.id.textview_uninstall);

        checkBoxStartServer.setTypeface(custom_font);
        checkBoxLoginAuto.setTypeface(custom_font);

        checkBoxStartServer.setChecked(prefs.getBoolean(Constants.PREFERENCE_AUTOSTART_SERVICE, false));
        checkBoxLoginAuto.setChecked(prefs.getBoolean(Constants.PREFERENCE_LOGIN_AUTO, false));

        serverConfiguration = (FontTextView) findViewById(R.id.view_server_configuration);
        serverConfiguration.setText(prefs.getString(Constants.PREFERENCE_SERVER_NAME, "US-WEST-0"));
        serverConfiguration.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (Logger.DEBUG) {
                    new ServerDialog(SettingsActivity.this).show();
                }
                return false;
            }
        });

        radioGroupConnection.setOnCheckedChangeListener(this);
        radioGroupRemoteControl.setOnCheckedChangeListener(this);
        radioGroupScreen.setOnCheckedChangeListener(this);

        systemServiceStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeploymentDialog(false);
            }
        });

        settingsTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        systemSettingsTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                systemSettings.setVisibility(View.VISIBLE);
                permissionsSettings.setVisibility(View.GONE);
                networkSettings.setVisibility(View.GONE);
            }
        });


        permissionSettingsTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                systemSettings.setVisibility(View.GONE);
                permissionsSettings.setVisibility(View.VISIBLE);
                networkSettings.setVisibility(View.GONE);
            }
        });


        networkSettingsTitle.setOnClickListener(new View.OnClickListener() {
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
                setBitrateSelectorsColor();
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
                setBitrateSelectorsColor();
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
                setBitrateSelectorsColor();
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
                setBitrateSelectorsColor();
            }
        });

        String installedVersion = Utils.getInstallationDetailsFromFile(this);
        int deploymentState = checkDeploymentState(installedVersion);
        systemServiceStatus.setPaintFlags(systemServiceStatus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        switch (deploymentState) {
            case DEPLOYMENT_STATE_NOT_INSTALLED:
                systemServiceStatus.setText("n/a (click to install)");
                break;
            case DEPLOYMENT_STATE_NEEDS_UPDATE:
                systemServiceStatus.setText(installedVersion + " (click to update)");
                break;
            case DEPLOYMENT_STATE_UP_TO_DATE:
                systemServiceStatus.setText("up-to-date (click to uninstall)");
                break;
        }

        initRadioGroups();
        initBitrateSelectors();
    }

    private void initBitrateSelectors() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        selectedBitRateWiFi = Arrays.asList(bitrateValues).indexOf(prefs.getString(Constants.PREFERENCE_BITRATE_WIFI, bitrateValues[selectedBitRateWiFi]));
        bitrateWiFiSelected.setText(bitrateOptions[selectedBitRateWiFi]);

        selectedBitRateMobile = Arrays.asList(bitrateValues).indexOf(prefs.getString(Constants.PREFERENCE_BITRATE_MOBILE, bitrateValues[selectedBitRateMobile]));
        bitrateMobileSelected.setText(bitrateOptions[selectedBitRateMobile]);

        setBitrateSelectorsColor();
    }

    private void setBitrateSelectorsColor() {
        int color = android.R.color.holo_red_light;
        switch (selectedBitRateWiFi) {
            case 1:
                color = android.R.color.holo_orange_dark;
                break;
            case 2:
                color = android.R.color.holo_orange_light;
                break;
            case 3:
                color = android.R.color.holo_green_light;
                break;
        }
        bitrateWiFiSelected.setTextColor(getResources().getColor(color));

        color = android.R.color.holo_red_light;
        switch (selectedBitRateMobile) {
            case 1:
                color = android.R.color.holo_orange_dark;
                break;
            case 2:
                color = android.R.color.holo_orange_light;
                break;
            case 3:
                color = android.R.color.holo_green_light;
                break;
        }
        bitrateMobileSelected.setTextColor(getResources().getColor(color));
    }

    public void onCheckboxClicked(View v) {
        switch (v.getId()) {
            case R.id.checkbox_start_server_boot:
                Utils.setBooleanPref(SettingsActivity.this, Constants.PREFERENCE_AUTOSTART_SERVICE, checkBoxStartServer.isChecked());
                break;

            case R.id.checkbox_login_auto:
                Utils.setBooleanPref(SettingsActivity.this, Constants.PREFERENCE_LOGIN_AUTO, checkBoxLoginAuto.isChecked());
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

    private void initRadioGroups() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        switch (prefs.getString(Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_PROMPT)) {
            case Constants.CONSTANT_STRING_PROMPT:
                radioGroupConnection.check(R.id.radio_prompt_connection);
                break;
            case Constants.CONSTANT_STRING_ALLOW:
                radioGroupConnection.check(R.id.radio_allow_connection);
                break;
            case Constants.CONSTANT_STRING_DENY:
                radioGroupConnection.check(R.id.radio_deny_connection);
                break;
        }
        switch (prefs.getString(Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_PROMPT)) {
            case Constants.CONSTANT_STRING_PROMPT:
                radioGroupRemoteControl.check(R.id.radio_prompt_remote_control);
                break;
            case Constants.CONSTANT_STRING_ALLOW:
                radioGroupRemoteControl.check(R.id.radio_allow_remote_control);
                break;
            case Constants.CONSTANT_STRING_DENY:
                radioGroupRemoteControl.check(R.id.radio_deny_remote_control);
                break;
        }
        switch (prefs.getString(Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_PROMPT)) {
            case Constants.CONSTANT_STRING_PROMPT:
                radioGroupScreen.check(R.id.radio_prompt_screen);
                break;
            case Constants.CONSTANT_STRING_ALLOW:
                radioGroupScreen.check(R.id.radio_allow_screen);
                break;
            case Constants.CONSTANT_STRING_DENY:
                radioGroupScreen.check(R.id.radio_deny_screen);
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (checkedId) {
            case R.id.radio_prompt_connection:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_PROMPT);
                break;
            case R.id.radio_allow_connection:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_ALLOW);
                break;
            case R.id.radio_deny_connection:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_DENY);
                break;
            case R.id.radio_prompt_remote_control:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_PROMPT);
                break;
            case R.id.radio_allow_remote_control:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_ALLOW);
                break;
            case R.id.radio_deny_remote_control:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_DENY);
                break;
            case R.id.radio_prompt_screen:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_PROMPT);
                break;
            case R.id.radio_allow_screen:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_ALLOW);
                break;
            case R.id.radio_deny_screen:
                Utils.setStringPref(SettingsActivity.this, Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_DENY);
                break;
        }
    }
}