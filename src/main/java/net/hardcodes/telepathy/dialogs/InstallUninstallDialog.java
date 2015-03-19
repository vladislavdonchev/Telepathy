package net.hardcodes.telepathy.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import net.hardcodes.telepathy.tools.ShellCommandExecutor;
import net.hardcodes.telepathy.tools.Utils;

/**
 * Created by MnQko on 4.2.2015 г..
 */
public class InstallUninstallDialog extends AlertDialog implements DialogInterface.OnClickListener {

    private static final String INSTALL_SCRIPT =
            "sleep 3\n" +
                    "mount -o rw,remount /system\n" +
                    "cat %s > /system/priv-app/Telepathy.apk.tmp\n" +
                    "chmod 644 /system/priv-app/Telepathy.apk.tmp\n" +
                    "pm uninstall %s\n" +
                    "mv /system/priv-app/Telepathy.apk.tmp /system/priv-app/Telepathy.apk\n" +
                    "pm install -r /system/priv-app/Telepathy.apk\n" +
                    "sleep 5\n" +
                    "am start -n net.hardcodes.telepathy/.activities.HomeScreenActivity";

    private static final String UNINSTALL_SCRIPT =
            "mount -o rw,remount /system\n" +
                    "rm /system/priv-app/Telepathy.apk\n" +
                    "rm /data/app/net.hardcodes.*\n" +
                    "rm %s\n" +
                    "pm uninstall net.hardcodes.telepathy\n" +
                    "sleep 5\n" +
                    "reboot";

    public static final String CONFIGURATION_FILE = "telepathyInstallation.cfg";

    public InstallUninstallDialog(Context context) {
        super(context);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog.equals(this)) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    deployInSystem();
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    showUninstallConfirmationDialog();
                    break;
            }
        } else {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                uninstall();
            }
        }
    }

    private void showUninstallConfirmationDialog() {
        new Builder(getContext()).setMessage("Uninstalling the application will require a device reboot. Do you want to proceed?")
                .setPositiveButton("Yes", this).setNegativeButton("No", this).show();
    }

    private void deployInSystem() {
        Toast.makeText(getContext(), "Installing system service. Please wait...", Toast.LENGTH_LONG).show();
        ShellCommandExecutor.getInstance().runCommand(String.format(INSTALL_SCRIPT,
                new String[]{
                        getContext().getPackageCodePath(),
                        getContext().getPackageName()
                }));
        Utils.writeInstallationDetailsToFile(getContext());
        gotoHomeScreen();
    }

    private void gotoHomeScreen() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(startMain);
    }

    private void uninstall() {
        Toast.makeText(getContext(), "Uninstalling application and rebooting system...", Toast.LENGTH_LONG).show();
           Utils.deleteInstallationDetails();
        ShellCommandExecutor.getInstance().runCommand(UNINSTALL_SCRIPT);
        gotoHomeScreen();
    }
}
