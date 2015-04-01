package net.hardcodes.telepathy.dialogs;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.views.FontButton;
import net.hardcodes.telepathy.views.FontTextView;

/**
 * Created by StereoPor on 21.3.2015 г..
 */
public class BaseDialog extends Dialog {

    private static int NO_CONTENT = -1;

    private LayoutInflater inflater;

    protected FontTextView title;
    protected FontTextView message;
    protected FontButton leftButton;
    protected FontButton rightButton;
    protected LinearLayout contentContainer;

    private View.OnClickListener onButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.view_dialog_base_button_left:
                    onLeftButtonClick();
                    break;
                case R.id.view_dialog_base_button_right:
                    onRightButtonClick();
                    break;
            }
        }
    };

    protected BaseDialog(Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);

        inflater = getLayoutInflater();
        RelativeLayout dialogLayout = (RelativeLayout) inflater.inflate(R.layout.view_dialog_base, null);

        title = (FontTextView) dialogLayout.findViewById(R.id.view_dialog_base_title);
        message = (FontTextView) dialogLayout.findViewById(R.id.view_dialog_base_message);
        leftButton = (FontButton) dialogLayout.findViewById(R.id.view_dialog_base_button_left);
        rightButton = (FontButton) dialogLayout.findViewById(R.id.view_dialog_base_button_right);
        contentContainer = (LinearLayout) dialogLayout.findViewById(R.id.view_dialog_base_content);

        leftButton.setOnClickListener(onButtonClickListener);
        rightButton.setOnClickListener(onButtonClickListener);

        setContentView(dialogLayout);
    }

    protected void setup(String titleText, int contentResource, String leftButtonText, String rightButtonText) {
        setup(titleText, null, contentResource, leftButtonText, rightButtonText);
    }

    protected void setup(String titleText, String messageText, String leftButtonText, String rightButtonText) {
        setup(titleText, messageText, NO_CONTENT, leftButtonText, rightButtonText);
    }

    protected void setup(String titleText, String leftButtonText, String rightButtonText) {
        setup(titleText, null, NO_CONTENT, leftButtonText, rightButtonText);
    }

    protected void setup(String titleText, String messageText, int contentResource, String leftButtonText, String rightButtonText) {
        setTitle(titleText);
        if (!TextUtils.isEmpty(messageText)) {
            message.setVisibility(View.VISIBLE);
            setMessage(messageText);
        }
        if (contentResource != NO_CONTENT) {
            View content = inflater.inflate(contentResource, null);
            ViewGroup.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            content.setLayoutParams(layoutParams);

            contentContainer.addView(content);
        }
        setLeftButtonText(leftButtonText);
        setRightButtonText(rightButtonText);
    }

    protected void setTitle(String titleText) {
        title.setText(titleText);
    }

    protected void setMessage(String messageText) {
        message.setText(messageText);
    }

    protected void setLeftButtonText(String leftButtonText) {
        leftButton.setText(leftButtonText);
    }

    protected void setRightButtonText(String rightButtonText) {
        rightButton.setText(rightButtonText);
    }

    protected void toggleFrame(boolean visible) {
        leftButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        rightButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        title.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    protected void onLeftButtonClick() {
        dismiss();
    }

    protected void onRightButtonClick() {
        dismiss();
    }
}