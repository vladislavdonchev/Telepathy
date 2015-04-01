package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Utils;
import net.hardcodes.telepathy.views.FontTextView;

public class LoginDialog extends BaseDialog implements View.OnClickListener {

    private EditText uidInput;
    private EditText passInput;
    private FontTextView registerAccount;
    private FontTextView loginOptions;
    private SharedPreferences prefs;

    private RegisterDialog registerDialog;

    public LoginDialog(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setup("Enter login credentials:", R.layout.view_login, "login", "cancel");
        uidInput = (EditText) contentContainer.findViewById(R.id.view_uid_input);
        uidInput.setTypeface(title.getTypeface());
        passInput = (EditText) contentContainer.findViewById(R.id.view_pass_input);
        passInput.setTypeface(title.getTypeface());
        registerAccount = (FontTextView) contentContainer.findViewById(R.id.view_register_account);
        registerAccount.setOnClickListener(this);
        loginOptions = (FontTextView) contentContainer.findViewById(R.id.view_login_options);
        loginOptions.setOnClickListener(this);

        registerDialog = new RegisterDialog(context);
    }

    @Override
    public void show() {
        super.show();
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        uidInput.setText(prefs.getString(Constants.PREFERENCE_UID, ""));
        passInput.setText(prefs.getString(Constants.PREFERENCE_PASS, ""));
    }

    public void setAuthenticationFailed(boolean authenticationFailed) {
        findViewById(R.id.view_authentication_error).setVisibility(authenticationFailed ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onLeftButtonClick() {
        String uid = uidInput.getText().toString();
        String pass = passInput.getText().toString();

        if (!TextUtils.isEmpty(uid)) {
            if (!TextUtils.isEmpty(pass)) {
                prefs.edit().putString(Constants.PREFERENCE_UID, uid).commit();
                prefs.edit().putString(Constants.PREFERENCE_PASS, pass).commit();
                ConnectionManager.getInstance().reconnect(getContext());
                super.onLeftButtonClick();
            } else {
                Telepathy.showLongToast("The password field cannot be empty!");
            }
        } else {
            Telepathy.showLongToast("The username field cannot be empty!");
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.view_register_account:
                    registerDialog.show();
                break;
            case R.id.view_login_options:

                break;
        }
        dismiss();
    }
}
