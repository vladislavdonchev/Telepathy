package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;

public class DebugDialog extends BaseDialog implements SharedPreferences.OnSharedPreferenceChangeListener {

    private TextView debugText;
    private final SharedPreferences prefs;

    public DebugDialog(Context context) {
        super(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setup("Debug Log:", R.layout.view_text, "clear", "close");
        debugText = (TextView) findViewById(R.id.view_text);
        findViewById(R.id.view_text_wrapper).setLayoutParams(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600));
    }

    @Override
    public void show() {
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateLog();
        super.show();
    }

    @Override
    public void dismiss() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.dismiss();
    }

    private void updateLog() {
        String currentLog = prefs.getString(Constants.PREFERENCE_DEBUG_LOG, "");
        debugText.setText(currentLog);
    }

    @Override
    protected void onLeftButtonClick() {
        prefs.edit().putString(Constants.PREFERENCE_DEBUG_LOG, "").apply();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(Constants.PREFERENCE_DEBUG_LOG)) {
            updateLog();
        }
    }
}
