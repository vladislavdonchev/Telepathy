package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.EditText;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.activities.RemoteControlActivity;

public class ConnectDialog extends BaseDialog {

    public static final String KEY_UID_EXTRA = "remoteUID";
    public static final String KEY_LAST_UID_PREF = "last_uid";

    private EditText uidInput;

    public ConnectDialog(Context context) {
        super(context);
        setup("Please enter user ID:", R.layout.view_uid_input, "connect", "cancel");
        uidInput = (EditText) contentContainer.findViewById(R.id.view_uid_input);
        uidInput.setTypeface(title.getTypeface());
    }

    @Override
    protected void onLeftButtonClick() {
        String address = uidInput.getText().toString();

        if (!address.equals("")) {
            SharedPreferences prefs = getContext().getSharedPreferences("MAIN_PREFS", Context.MODE_PRIVATE);
            Intent startIntent = new Intent(getContext(), RemoteControlActivity.class);
            startIntent.putExtra(KEY_UID_EXTRA, address);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_LAST_UID_PREF, address);
            editor.commit();
            getContext().startActivity(startIntent);
            super.onLeftButtonClick();
        }
    }
}
