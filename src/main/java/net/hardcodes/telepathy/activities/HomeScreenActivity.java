package net.hardcodes.telepathy.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.gson.Gson;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.dialogs.ConnectDialog;
import net.hardcodes.telepathy.dialogs.DebugDialog;
import net.hardcodes.telepathy.model.User;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Logger;
import net.hardcodes.telepathy.tools.Utils;
import net.hardcodes.telepathy.views.FontButton;

public class HomeScreenActivity extends BaseActivity {

    private DebugDialog debugDialog;
    private ConnectDialog connectDialog;
    private FontButton userButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.splunk(this);
        setContents(R.layout.activity_home_screen);
        showDeploymentDialog(true);
        initViews();
        prefs.edit().putString(Constants.PREFERENCE_DEBUG_LOG, "").apply();
        checkGooglePlay();
    }

    private void checkGooglePlay() {
        int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            Telepathy.showLongToast("Google Play " + GooglePlayServicesUtil.getErrorString(result).replace("_", " ")
                    .toLowerCase() + "!");
            GooglePlayServicesUtil.getErrorDialog(result, this, 0).show();
            GooglePlayServicesUtil.showErrorNotification(result, this);
        }
    }

    private void initViews() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        userButton = (FontButton) findViewById(R.id.activity_home_screen_user_button);
        userButton.setText(prefs.getString(Constants.PREFERENCE_UID, "click to log in"));
        userButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Telepathy.attemptLogin(false);
            }
        });

        FontButton connectButton = (FontButton) findViewById(R.id.activity_home_screen_connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectDialog.show();
                if (!ConnectionManager.getInstance().isConnectedAndAuthenticated()) {
                    Telepathy.showLongToast("Please log in first!");
                    Telepathy.attemptLogin(false);
                }
            }
        });

        ImageView settings = (ImageView) findViewById(R.id.activity_home_screen_settings_button);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeScreenActivity.this, SettingsActivity.class));
            }
        });

        ImageView logo = (ImageView) findViewById(R.id.activity_home_screen_logo_button);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Logger.DEBUG) {
                    debugDialog.show();
                }
            }
        });


        connectDialog = new ConnectDialog(this);
        debugDialog = new DebugDialog(this);
    }

    @Override
    protected void checkServiceState() {
        super.checkServiceState();
    }

    @Override
    protected void checkConnectionState() {
        super.checkConnectionState();
        if (ConnectionManager.getInstance().isConnectedAndAuthenticated()) {
            userButton.setText(prefs.getString(Constants.PREFERENCE_UID, "click to log in"));
        } else {
            userButton.setText("click to log in");
        }
    }
}
