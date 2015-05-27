package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import net.hardcodes.telepathy.views.FontEditText;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.activities.RemoteControlActivity;

public class ConnectDialog extends BaseDialog {

    public static final String KEY_UID_EXTRA = "remoteUID";
    public static final String KEY_LAST_UID_PREF = "last_uid";

    private FontEditText uidInput;
    private final SharedPreferences prefs;

    public ConnectDialog(Context context) {
        super(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setup("Please enter user ID:", R.layout.view_text_input, "connect", "cancel");
        uidInput = (FontEditText) contentContainer.findViewById(R.id.view_text_input);
        uidInput.setTypeface(title.getTypeface());
        uidInput.setText(prefs.getString(KEY_LAST_UID_PREF, ""));

        uidInput.setBackPressedListener(keyboardEventListener);
    }

    @Override
    protected void onLeftButtonClick() {
        String uid = uidInput.getText().toString();

        if (!uid.equals("")) {
            Intent startIntent = new Intent(getContext(), RemoteControlActivity.class);
            startIntent.putExtra(KEY_UID_EXTRA, uid);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_LAST_UID_PREF, uid);
            editor.commit();
            getContext().startActivity(startIntent);
            super.onLeftButtonClick();
        }
    }

    @Override
    public void show() {
        super.show();
    }
}
