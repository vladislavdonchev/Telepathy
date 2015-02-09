package net.hardcodes.telepathy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.hardcodes.telepathy.tools.Utils;

public class PasswordDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final LinearLayout dialogLayout = (LinearLayout) inflater.inflate(R.layout.dialog_connect, null);
        final EditText passInput = (EditText) dialogLayout.findViewById(R.id.uid_input);
        passInput.setHint("");
        passInput.setInputType(InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setTitle("Input Password");
        builder.setView(dialogLayout)
                // Add action buttons
                .setPositiveButton("Proceed", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                        String password = passInput.getText().toString();
                        if (Utils.sha256(password).toUpperCase().equals("A67D22CEF2F6639D71B8901B5B2BBEE4A2400D92C70E60C179C0FD76D72D6C23")) {
                            new ConnectDialog().show(getFragmentManager(), "Remote Control");
                        } else {
                            Toast.makeText(getActivity(), "Password incorrect.", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        PasswordDialog.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }
}
