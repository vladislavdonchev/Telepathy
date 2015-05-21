package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.CheckBox;
import android.widget.EditText;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;

public class ServerDialog extends BaseDialog {

    private EditText serverNameInput;
    private EditText serverAddressInput;
    private CheckBox serverEncryption;
    private CheckBox pingInitiation;
    private final SharedPreferences prefs;

    public ServerDialog(Context context) {
        super(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setup("Server Configuration:", R.layout.view_server_config, "save", "cancel");
        serverNameInput = (EditText) contentContainer.findViewById(R.id.view_server_name_input);
        serverNameInput.setTypeface(title.getTypeface());
        serverNameInput.setText(prefs.getString(Constants.PREFERENCE_SERVER_NAME, "US-WEST-0"));
        serverAddressInput = (EditText) contentContainer.findViewById(R.id.view_server_address_input);
        serverAddressInput.setTypeface(title.getTypeface());
        serverAddressInput.setText(prefs.getString(Constants.PREFERENCE_SERVER_ADDRESS, "telepathy.hardcodes.net:443/tp"));

        serverEncryption = (CheckBox) contentContainer.findViewById(R.id.view_server_tls_config);
        serverEncryption.setTypeface(title.getTypeface());
        serverEncryption.setChecked(prefs.getBoolean(Constants.PREFERENCE_USE_TLS, true));

        pingInitiation = (CheckBox) contentContainer.findViewById(R.id.view_server_ping_config);
        pingInitiation.setTypeface(title.getTypeface());
        pingInitiation.setChecked(prefs.getBoolean(Constants.PREFERENCE_INITIATE_PING, false));
    }

    @Override
    protected void onLeftButtonClick() {
        String serverName = serverNameInput.getText().toString();
        String serverAddress = serverAddressInput.getText().toString();

        if (!serverNameInput.equals("") && !serverAddress.equals("")) {
            prefs.edit()
            .putString(Constants.PREFERENCE_SERVER_NAME, serverName)
            .putString(Constants.PREFERENCE_SERVER_ADDRESS, serverAddress)
            .putBoolean(Constants.PREFERENCE_USE_TLS, serverEncryption.isChecked())
            .putBoolean(Constants.PREFERENCE_INITIATE_PING, pingInitiation.isChecked()).apply();

            super.onLeftButtonClick();
        }
    }
}
