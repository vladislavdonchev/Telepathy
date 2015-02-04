package net.hardcodes.telepathy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.LinearLayout;

public class ConnectDialog extends DialogFragment {

    public static final String KEY_UID_EXTRA = "remoteUID";
    public static final String KEY_LAST_UID_PREF = "last_uid";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final SharedPreferences prefs = getActivity().getSharedPreferences("MAIN_PREFS", Context.MODE_PRIVATE);
        String lastUID = prefs.getString(KEY_LAST_UID_PREF, "");

        final LinearLayout dialogLayout = (LinearLayout) inflater.inflate(R.layout.dialog_address_input, null);
        final EditText uidInput = (EditText) dialogLayout.findViewById(R.id.uid_input);
        uidInput.setText(lastUID);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setTitle("Remote Control");
        builder.setView(dialogLayout)
                // Add action buttons
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                        String address = uidInput.getText().toString();
                        if (!address.equals("")) {
                            Intent startIntent = new Intent(getActivity(), RemoteControlActivity.class);
                            startIntent.putExtra(KEY_UID_EXTRA, address);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(KEY_LAST_UID_PREF, address);
                            editor.commit();
                            startActivity(startIntent);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ConnectDialog.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }
}
