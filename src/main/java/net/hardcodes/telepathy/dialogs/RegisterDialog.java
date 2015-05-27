package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import net.hardcodes.telepathy.views.FontEditText;
import android.widget.Toast;

import com.koushikdutta.async.ByteBufferList;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.model.User;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Utils;

public class RegisterDialog extends BaseDialog implements ConnectionManager.WebSocketConnectionListener, DialogInterface.OnDismissListener {

    private View connectionProgress;
    private View registrationForm;

    private FontEditText uidInput;
    private FontEditText passInput;
    private FontEditText passConfirmInput;
    private FontEditText emailInput;
    private SharedPreferences prefs;


    public RegisterDialog(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setup("Create account:", R.layout.view_register, "create", "cancel");

        connectionProgress = findViewById(R.id.view_connection_progress);
        registrationForm = findViewById(R.id.view_registration_form);

        uidInput = (FontEditText) contentContainer.findViewById(R.id.view_uid_input);
        uidInput.setTypeface(title.getTypeface());
        passInput = (FontEditText) contentContainer.findViewById(R.id.view_pass_input);
        passInput.setTypeface(title.getTypeface());
        passConfirmInput = (FontEditText) contentContainer.findViewById(R.id.view_pass_confirm_input);
        passConfirmInput.setTypeface(title.getTypeface());
        emailInput = (FontEditText) contentContainer.findViewById(R.id.view_email_input);
        emailInput.setTypeface(title.getTypeface());

        uidInput.setBackPressedListener(keyboardEventListener);
        passInput.setBackPressedListener(keyboardEventListener);
        passConfirmInput.setBackPressedListener(keyboardEventListener);
        emailInput.setBackPressedListener(keyboardEventListener);

        findViewById(R.id.view_email_information).setSelected(true);

        toggleFrame(false);
        setOnDismissListener(this);
    }

    public void setRegistrationFailed(boolean authenticationFailed) {
        findViewById(R.id.view_registration_error).setVisibility(authenticationFailed ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onLeftButtonClick() {
        String uid = uidInput.getText().toString();
        String pass = passInput.getText().toString();
        String passConfirm = passConfirmInput.getText().toString();

        User user = new User();
        user.setUserName(uid);
        user.setPasswordHash(Utils.sha256(pass));
        user.setMailAddress(emailInput.getText().toString());

        if (!TextUtils.isEmpty(uid)) {
            if (!TextUtils.isEmpty(pass)) {
                if (pass.equals(passConfirm)) {
                    ConnectionManager.getInstance().registerAccount(user);
                } else {
                    Toast.makeText(getContext(), "The passwords do not match!", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getContext(), "Password cannot be empty!", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getContext(), "User name cannot be empty!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionAcquired() {
        Telepathy.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                toggleFrame(true);
                connectionProgress.setVisibility(View.GONE);
                registrationForm.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onError(int errorCode) {
        switch (errorCode) {
            case ConnectionManager.ERROR_CODE_SERVER_UNAVAILABLE:
            case ConnectionManager.ERROR_CODE_TLS_CONFIG_FAILED:
                dismiss();
                break;
            case TelepathyAPI.ERROR_USER_ID_TAKEN:
                Telepathy.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        setRegistrationFailed(true);
                    }
                });
                break;
        }
    }

    @Override
    public void onTextMessage(String message) {
        if (message.startsWith(TelepathyAPI.MESSAGE_REGISTRATION_SUCCESS)) {
            setRegistrationFailed(false);
            prefs.edit().putString(Constants.PREFERENCE_UID, uidInput.getText().toString()).commit();
            prefs.edit().putString(Constants.PREFERENCE_PASS, "").commit();
            Telepathy.showLongToast("Account created successfully!");
            Telepathy.attemptLogin(false);
            dismiss();
        }
    }

    @Override
    public void onBinaryMessage(ByteBufferList byteBufferList) {

    }

    @Override
    public void show() {
        super.show();
        ConnectionManager.getInstance().acquireConnection(getContext(), this);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        ConnectionManager.getInstance().releaseConnection(getContext());
    }
}
