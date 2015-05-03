package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.CheckBox;
import android.widget.EditText;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.tools.ConnectionManager;

public class ServerDialog extends BaseDialog {

    private EditText serverNameInput;
    private EditText serverAddressInput;
    private CheckBox serverEncryption;
    private final SharedPreferences prefs;

    public ServerDialog(Context context) {
        super(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setup("Server Configuration:", R.layout.view_server_config, "save", "cancel");
        serverNameInput = (EditText) contentContainer.findViewById(R.id.view_server_name_input);
        serverNameInput.setTypeface(title.getTypeface());
        serverNameInput.setText(prefs.getString(Constants.PREFERENCE_SERVER_NAME, "EUR-BM0"));
        serverAddressInput = (EditText) contentContainer.findViewById(R.id.view_server_address_input);
        serverAddressInput.setTypeface(title.getTypeface());
        serverAddressInput.setText(prefs.getString(Constants.PREFERENCE_SERVER_ADDRESS, "54.68.141.75:8021/tp"));

        serverEncryption = (CheckBox) contentContainer.findViewById(R.id.view_server_tls_config);
        serverEncryption.setTypeface(title.getTypeface());
        serverEncryption.setChecked(prefs.getBoolean(Constants.PREFERENCE_USE_TLS, false));
    }

    @Override
    protected void onLeftButtonClick() {
        String serverName = serverNameInput.getText().toString();
        String serverAddress = serverAddressInput.getText().toString();

        if (!serverNameInput.equals("") && !serverAddress.equals("")) {
            prefs.edit().putString(Constants.PREFERENCE_SERVER_NAME, serverName).commit();
            prefs.edit().putString(Constants.PREFERENCE_SERVER_ADDRESS, serverAddress).commit();
            prefs.edit().putBoolean(Constants.PREFERENCE_USE_TLS, serverEncryption.isChecked()).commit();

            super.onLeftButtonClick();
        }
    }
}
