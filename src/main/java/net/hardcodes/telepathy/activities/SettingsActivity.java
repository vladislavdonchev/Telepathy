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
import net.hardcodes.telepathy.tools.Utils;
import net.hardcodes.telepathy.views.FontTextView;

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
        serverConfiguration.setText(prefs.getString(Constants.PREFERENCE_SERVER_NAME, "EUR-BM0"));
        serverConfiguration.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new ServerDialog(SettingsActivity.this).show();
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