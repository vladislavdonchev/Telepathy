package net.hardcodes.telepathy.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.dialogs.ConnectDialog;
import net.hardcodes.telepathy.views.FontButton;

public class HomeScreenActivity extends BaseActivity {

    private ConnectDialog connectDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContents(R.layout.activity_home_screen);
        showDeploymentDialog(true);
        initViews();
    }

    private void initViews() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        FontButton userButton = (FontButton) findViewById(R.id.activity_home_screen_user_button);
        userButton.setText(preferences.getString("uid", "111"));
        userButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO
            }
        });

        FontButton connectButton = (FontButton) findViewById(R.id.activity_home_screen_connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectDialog.show();
            }
        });

        ImageView settings = (ImageView) findViewById(R.id.activity_homne_screen_settings_button);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeScreenActivity.this, SettingsActivity.class));
            }
        });

        connectDialog = new ConnectDialog(this);
    }
}
