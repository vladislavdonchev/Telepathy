package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.EditText;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.tools.ConnectionManager;

public class LoginDialog extends BaseDialog {

    private EditText uidInput;
    private final SharedPreferences prefs;

    public LoginDialog(Context context) {
        super(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setup("Enter login credentials:", R.layout.view_text_input, "login", "cancel");
        uidInput = (EditText) contentContainer.findViewById(R.id.view_text_input);
        uidInput.setTypeface(title.getTypeface());
        uidInput.setText(prefs.getString("uid", "111"));
    }

    @Override
    protected void onLeftButtonClick() {
        String uid = uidInput.getText().toString();

        if (!uid.equals("")) {
            prefs.edit().putString("uid", uid).commit();
            ConnectionManager.getInstance().reconnect(getContext());
            super.onLeftButtonClick();
        }
    }
}
