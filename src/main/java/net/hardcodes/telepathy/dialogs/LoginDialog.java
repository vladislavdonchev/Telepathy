package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import com.koushikdutta.async.ByteBufferList;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.views.FontTextView;

public class LoginDialog extends BaseDialog implements View.OnClickListener, ConnectionManager.WebSocketConnectionListener, DialogInterface.OnDismissListener {

    private EditText uidInput;
    private EditText passInput;
    private CheckBox passSaveCheckbox;
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
        passSaveCheckbox = (CheckBox) findViewById(R.id.view_login_save_password);
        passSaveCheckbox.setTypeface(title.getTypeface());
        registerAccount = (FontTextView) contentContainer.findViewById(R.id.view_register_account);
        registerAccount.setOnClickListener(this);
        loginOptions = (FontTextView) contentContainer.findViewById(R.id.view_login_options);
        loginOptions.setOnClickListener(this);

        registerDialog = new RegisterDialog(context);

        setOnDismissListener(this);
    }

    @Override
    public void show() {
        super.show();
        ConnectionManager.getInstance().acquireConnection(getContext(), this);
        setAuthenticationFailed(false);
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        uidInput.setText(prefs.getString(Constants.PREFERENCE_UID, ""));
        passInput.setText(prefs.getString(Constants.PREFERENCE_PASS, ""));
        passSaveCheckbox.setChecked(prefs.getBoolean(Constants.PREFERENCE_SAVE_PASSWORD, false));
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
                ConnectionManager.getInstance().login(getContext());
                dismiss();
                if (!passSaveCheckbox.isChecked()) {
                    prefs.edit().putString(Constants.PREFERENCE_PASS, "").commit();
                }
            } else {
                prefs.edit().putString(Constants.PREFERENCE_PASS, "").commit();
                passSaveCheckbox.setChecked(false);
                Telepathy.showLongToast("The password field cannot be empty!");
            }
            prefs.edit().putBoolean(Constants.PREFERENCE_SAVE_PASSWORD, passSaveCheckbox.isChecked()).commit();
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

    @Override
    public void onConnect() {
    }

    @Override
    public void onError(int errorCode) {
        switch (errorCode) {
            case ConnectionManager.WebSocketConnectionListener.ERROR_CODE_SERVER_UNAVAILABLE:
                Telepathy.showLongToast("The server is not available! Please try again later.");
                dismiss();
                break;
        }
    }

    @Override
    public void onTextMessage(String message) {
    }

    @Override
    public void onBinaryMessage(ByteBufferList byteBufferList) {

    }

    @Override
    public void onDisconnect() {
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        ConnectionManager.getInstance().releaseConnection(getContext());
    }
}
