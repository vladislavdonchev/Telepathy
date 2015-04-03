package net.hardcodes.telepathy.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.RemoteControlService;
import net.hardcodes.telepathy.dialogs.InstallUninstallDialog;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.views.FontButton;
import net.hardcodes.telepathy.tools.ShellCommandExecutor;
import net.hardcodes.telepathy.tools.Utils;

/**
 * Created by StereoPor on 22.3.2015 г..
 */
public class BaseActivity extends Activity {
    protected final static int DEPLOYMENT_STATE_NOT_INSTALLED = 0;
    protected final static int DEPLOYMENT_STATE_NEEDS_UPDATE = 1;
    protected final static int DEPLOYMENT_STATE_UP_TO_DATE = 2;

    private ServiceStateChangeReceiver serviceStateChangeReceiver;

    private BroadcastReceiver connectionStateChangeReceiver;
    private FontButton serviceAndConnectionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        serviceAndConnectionStatus = (FontButton) findViewById(R.id.server_control_button);
    }

    protected void setContents(int contentsResource) {
        ((LinearLayout) findViewById(R.id.activity_contents)).addView(getLayoutInflater().inflate(contentsResource, null));
    }

    protected void showDeploymentDialog(boolean showOnlyIfNeeded) {
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
        if (connectionStateChangeReceiver == null) {
            connectionStateChangeReceiver = new ConnectionStateChangeReceiver();
        }
        intentFilter = new IntentFilter(ConnectionManager.ACTION_CONNECTION_STATE_CHANGE);
        registerReceiver(connectionStateChangeReceiver, intentFilter);
        checkConnectionState();
        super.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(serviceStateChangeReceiver);
        unregisterReceiver(connectionStateChangeReceiver);
        super.onPause();
    }

    public void toggleService(View v) {
        if (Utils.isServiceRunning(this, RemoteControlService.class) && ConnectionManager.getInstance().isConnectedAndAuthenticated()) {
            Utils.stopService(this);
        } else {
            Utils.startService(this);
        }
    }

    private class ServiceStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkServiceState();
        }
    }

    private class ConnectionStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkConnectionState();
        }
    }

    protected void checkServiceState() {
        if (Utils.isServiceRunning(this, RemoteControlService.class)) {
            serviceAndConnectionStatus.setText("service is running");
        } else {
            serviceAndConnectionStatus.setText("click to start service");
        }
    }

    protected void checkConnectionState() {
        if (ConnectionManager.getInstance().isConnectedAndAuthenticated()) {
            serviceAndConnectionStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            serviceAndConnectionStatus.setTextColor(Color.parseColor("#FFFFFF"));
        }
    }

    protected int checkDeploymentState(String installedVersion) {
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
}
