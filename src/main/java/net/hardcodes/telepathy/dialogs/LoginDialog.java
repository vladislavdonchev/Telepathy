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

    private View connectionProgress;
    private View loginForm;

    private EditText uidInput;
    private EditText passInput;
    private CheckBox passSaveCheckbox;
    private FontTextView registerAccount;
    private FontTextView loginOptions;
    private SharedPreferences prefs;

    private RegisterDialog registerDialog;
    private boolean previousAuthenticationFailed;

    public LoginDialog(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setup("Enter login credentials:", R.layout.view_login, "login", "cancel");

        connectionProgress = findViewById(R.id.view_connection_progress);
        loginForm = findViewById(R.id.view_login_form);

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

        toggleFrame(false);
        setOnDismissListener(this);
    }

    public void show(boolean previousAuthenticationFailed) {
        super.show();
        this.previousAuthenticationFailed = previousAuthenticationFailed;
        ConnectionManager.getInstance().acquireConnection(getContext(), this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        uidInput.setText(prefs.getString(Constants.PREFERENCE_UID, ""));
        passInput.setText(prefs.getString(Constants.PREFERENCE_PASS, ""));
        passSaveCheckbox.setChecked(prefs.getBoolean(Constants.PREFERENCE_SAVE_PASSWORD, false));
        findViewById(R.id.view_authentication_error).setVisibility(previousAuthenticationFailed ? View.VISIBLE : View.GONE);

        if (ConnectionManager.getInstance().isConnectedAndAuthenticated()) {
            setTitle("User details:");
            setLeftButtonText("logout");
        } else {
            setTitle("Enter login credentials:");
            setLeftButtonText("login");
        }
    }

    @Override
    protected void onLeftButtonClick() {
        if (!ConnectionManager.getInstance().isConnectedAndAuthenticated()) {
            String uid = uidInput.getText().toString();
            String pass = passInput.getText().toString();

            if (!TextUtils.isEmpty(uid)) {
                if (!TextUtils.isEmpty(pass)) {
                    prefs.edit().putString(Constants.PREFERENCE_UID, uid).commit();
                    prefs.edit().putString(Constants.PREFERENCE_PASS, pass).commit();
                    ConnectionManager.getInstance().login(getContext());
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
        } else {
            ConnectionManager.getInstance().logout();
            prefs.edit().putString(Constants.PREFERENCE_PASS, "").commit();
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
    public void onConnectionAcquired() {
        final boolean isConnectedAndAuthenticated = ConnectionManager.getInstance().isConnectedAndAuthenticated();

        Telepathy.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                toggleFrame(true);
                connectionProgress.setVisibility(View.GONE);

                if (!isConnectedAndAuthenticated) {
                    loginForm.setVisibility(View.VISIBLE);
                } else {
                    loginForm.setVisibility(View.GONE);
                }
            }
        });

        if (!isConnectedAndAuthenticated && !previousAuthenticationFailed) {
            boolean autoLogin = prefs.getBoolean(Constants.PREFERENCE_LOGIN_AUTO, false);
            if (autoLogin && !TextUtils.isEmpty(prefs.getString(Constants.PREFERENCE_PASS, ""))) {
                ConnectionManager.getInstance().login(getContext());
                dismiss();
            }
        }
    }

    @Override
    public void onError(int errorCode) {
        switch (errorCode) {
            case ConnectionManager.ERROR_CODE_SERVER_UNAVAILABLE:
            case ConnectionManager.ERROR_CODE_TLS_CONFIG_FAILED:
                dismiss();
                break;
        }
    }

    @Override
    public void onTextMessage(String message) {
        if (message.startsWith(TelepathyAPI.MESSAGE_LOGIN_SUCCESS) || message.startsWith(TelepathyAPI.MESSAGE_LOGOUT_SUCCESS)) {
            dismiss();
        }
    }

    @Override
    public void onBinaryMessage(ByteBufferList byteBufferList) {

    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        ConnectionManager.getInstance().releaseConnection(getContext());
    }
}
