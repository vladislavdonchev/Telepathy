package net.hardcodes.telepathy;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import net.hardcodes.telepathy.tools.ShellCommandExecutor;

public class HomeScreenActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boolean isAppDeployedInSystem = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("appDeployed", false);
        if (!isAppDeployedInSystem) {
            if (ShellCommandExecutor.isSUAvailable()) {
                InstallUninstallDialog installUninstallDialog = new InstallUninstallDialog(this);
                installUninstallDialog.setTitle("Application Deployment");
                installUninstallDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Install", installUninstallDialog);
                installUninstallDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Uninstall", installUninstallDialog);
                installUninstallDialog.show();
            } else {
                Toast.makeText(this, "SU not available. This device can only be used as a client.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void startClient(View v) {
        new ConnectDialog().show(getFragmentManager(), "Remote Control");
    }

    public void startServer(View v) {
        Intent startServerIntent = new Intent(HomeScreenActivity.this, RemoteControlService.class);
        startServerIntent.setAction("START");
        startService(startServerIntent);
        finish();
    }
}
