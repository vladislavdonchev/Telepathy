package net.hardcodes.telepathy;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.koushikdutta.async.Util;

import net.hardcodes.telepathy.tools.ShellCommandExecutor;
import net.hardcodes.telepathy.tools.Utils;

public class HomeScreenActivity extends Activity {

    private final static int DEPLOYMENT_STATE_NOT_INSTALLED = 0;
    private final static int DEPLOYMENT_STATE_NEEDS_UPDATE = 1;
    private final static int DEPLOYMENT_STATE_UP_TO_DATE = 2;

    private ServiceStateChangeReceiver serviceStateChangeReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_screen);

        showDeploymentDialog(true);
    }

    private void showDeploymentDialog(boolean showOnlyIfNeeded) {
        String installedVersion = Utils.getInstallationDetailsFromFile(this);
        int deploymentState = checkDeploymentState(installedVersion);

        if (ShellCommandExecutor.isSUAvailable()) {
            if (!showOnlyIfNeeded || (showOnlyIfNeeded && deploymentState != DEPLOYMENT_STATE_UP_TO_DATE)) {
                buildDeploymentDialog(deploymentState, installedVersion);
            }
        } else {
            Toast.makeText(this, "SU not available. This device can only be used as a client.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        if (serviceStateChangeReceiver == null) {
            serviceStateChangeReceiver = new ServiceStateChangeReceiver();
        }
        IntentFilter intentFilter = new IntentFilter(RemoteControlService.ACTION_SERVICE_STATE_CHANGED);
        registerReceiver(serviceStateChangeReceiver, intentFilter);
        checkServiceState();
        super.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(serviceStateChangeReceiver);
        super.onPause();
    }


    private int checkDeploymentState(String installedVersion) {
        boolean isAppDeployedInSystem = !TextUtils.isEmpty(installedVersion);

        int state = DEPLOYMENT_STATE_UP_TO_DATE;
        if (!isAppDeployedInSystem) {
            state = DEPLOYMENT_STATE_NOT_INSTALLED;
        }
        if (isAppDeployedInSystem && !getString(R.string.app_version).equals(installedVersion)) {
            state = DEPLOYMENT_STATE_NEEDS_UPDATE;
        }

        return state;
    }

    private void buildDeploymentDialog(int state, String installedVersion) {
        InstallUninstallDialog installUninstallDialog = new InstallUninstallDialog(this);
        String message = "In order to be able to remote control this device you will need to deploy the application as a system service.\nPress 'Install' to begin the process.";
        String positiveText = "Install";

        switch (state) {
            case DEPLOYMENT_STATE_UP_TO_DATE:
                message = "Press 'Re-Install' to redeploy the remote control service.\nSelect 'Uninstall' to remove the application.";
                positiveText = "Re-Install";
                installUninstallDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Uninstall", installUninstallDialog);
                break;
            case DEPLOYMENT_STATE_NEEDS_UPDATE:
                message = "Press 'Update' to install version " + getString(R.string.app_version) + " of the remote control service.";
                positiveText = "Update";
                break;
        }

        installUninstallDialog.setTitle("Application Deployment");
        installUninstallDialog.setMessage(message);
        installUninstallDialog.setButton(DialogInterface.BUTTON_POSITIVE, positiveText, installUninstallDialog);
        installUninstallDialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_deployment).setVisible(ShellCommandExecutor.isSUAvailable());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_deployment:
                showDeploymentDialog(false);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void startClient(View v) {
        new ConnectDialog().show(getFragmentManager(), "Remote Control");
    }

    public void toggleService(View v) {
        Utils.toggleService(this);
    }

    private class ServiceStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkServiceState();
        }
    }

    private void checkServiceState() {
        if (Utils.isServiceRunning(this, RemoteControlService.class)) {
            ((Button) findViewById(R.id.server_control_button)).setText("Stop Service");
        } else {
            ((Button) findViewById(R.id.server_control_button)).setText("Start Service");
        }
    }
}
