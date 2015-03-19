package net.hardcodes.telepathy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.RelativeLayout;

import net.hardcodes.telepathy.activities.RemoteControlActivity;
import net.hardcodes.telepathy.model.FontButton;

public class ConnectDialog extends DialogFragment {

    public static final String KEY_UID_EXTRA = "remoteUID";
    public static final String KEY_LAST_UID_PREF = "last_uid";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final SharedPreferences prefs = getActivity().getSharedPreferences("MAIN_PREFS", Context.MODE_PRIVATE);
        String lastUID = prefs.getString(KEY_LAST_UID_PREF, "");

        final RelativeLayout dialogLayout = (RelativeLayout) inflater.inflate(R.layout.dialog_connect, null);
        final EditText uidInput = (EditText) dialogLayout.findViewById(R.id.uid_input);
        FontButton buttonCancel = (FontButton) dialogLayout.findViewById(R.id.btn_cancel);
        FontButton buttonConnect = (FontButton) dialogLayout.findViewById(R.id.btn_connect);
        uidInput.setText(lastUID);
        uidInput.setTypeface(buttonCancel.getTypeface());

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConnectDialog.this.getDialog().cancel();
            }
        });

        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        });

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setContentView(dialogLayout);


        return dialog;
    }
}
