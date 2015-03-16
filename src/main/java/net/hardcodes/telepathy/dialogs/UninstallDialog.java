package net.hardcodes.telepathy.dialogs;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.RelativeLayout;
import android.widget.Toast;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.tools.ShellCommandExecutor;
import net.hardcodes.telepathy.tools.Utils;

/**
 * Created by aleksandardimitrov on 3/16/15.
 */
public class UninstallDialog extends DialogFragment {
    private static final String UNINSTALL_SCRIPT =
            "mount -o rw,remount /system\n" +
                    "rm /system/priv-app/Telepathy.apk\n" +
                    "rm /data/app/net.hardcodes.*\n" +
                    "rm %s\n" +
                    "pm uninstall net.hardcodes.telepathy\n" +
                    "sleep 5\n" +
                    "reboot";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final RelativeLayout dialogLayout = (RelativeLayout) inflater.inflate(R.layout.dialog_choice, null);
        dialogLayout.findViewById(R.id.button_no).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UninstallDialog.this.getDialog().cancel();
            }
        });

        dialogLayout.findViewById(R.id.button_yes).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uninstall();
            }
        });

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setContentView(dialogLayout);
        return dialog;
    }

    private void uninstall() {
        Toast.makeText(getActivity(), "Uninstalling application and rebooting system...", Toast.LENGTH_LONG).show();
        Utils.deleteInstallationDetails();
        ShellCommandExecutor.getInstance().runCommand(UNINSTALL_SCRIPT);
        gotoHomeScreen();
    }

    private void gotoHomeScreen() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getActivity().startActivity(startMain);
    }
}
