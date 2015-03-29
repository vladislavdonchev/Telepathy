package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.EditText;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.tools.ConnectionManager;

public class LoginDialog extends BaseDialog {

    private EditText uidInput;
    private EditText passInput;
    private SharedPreferences prefs;

    public LoginDialog(Context context, boolean authenticationFailed) {
        super(context);
        init(context, authenticationFailed);
    }

    public LoginDialog(Context context) {
        super(context);
        init(context, false);
    }

    private void init(Context context, boolean authenticationFailed) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setup("Enter login credentials:", R.layout.view_login, "login", "cancel");
        uidInput = (EditText) contentContainer.findViewById(R.id.view_uid_input);
        uidInput.setTypeface(title.getTypeface());
        uidInput.setText(prefs.getString(Constants.PREFERENCE_UID, ""));
        passInput = (EditText) contentContainer.findViewById(R.id.view_pass_input);
        passInput.setTypeface(title.getTypeface());
        passInput.setText(prefs.getString(Constants.PREFERENCE_PASS, ""));

        if (authenticationFailed) {
            findViewById(R.id.view_authentication_error).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onLeftButtonClick() {
        String uid = uidInput.getText().toString();
        String pass = passInput.getText().toString();

        if (!uid.equals("")) {
            prefs.edit().putString(Constants.PREFERENCE_UID, uid).commit();
            prefs.edit().putString(Constants.PREFERENCE_PASS, pass).commit();
            ConnectionManager.getInstance().reconnect(getContext());
            super.onLeftButtonClick();
        }
    }
}
