package net.hardcodes.telepathy.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;

public class ProgressDialog extends BaseDialog {

    private TextView progressText;

    public ProgressDialog(Context context, String message) {
        super(context);
        setup("", R.layout.view_progress, "", "");
        toggleFrame(false);
        progressText = (TextView) findViewById(R.id.view_progress_text);
        progressText.setText(message);
    }
}
