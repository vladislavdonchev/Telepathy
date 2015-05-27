package net.hardcodes.telepathy.views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import net.hardcodes.telepathy.tools.Logger;

/**
 * Created by StereoPor on 26.5.2015 ?..
 */
public class FontEditText extends EditText implements View.OnClickListener {

    private KeyboardEventListener keyboardEventListener;

    public FontEditText(Context context) {
        super(context);
        init();
    }

    public FontEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FontEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOnClickListener(this);
        //TODO add font initialization.
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (focused) {
            if (keyboardEventListener != null) {
                keyboardEventListener.onOpen(this);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public void onClick(View v) {
        if (keyboardEventListener != null) {
            keyboardEventListener.onOpen(this);
        }
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        //Logger.log("KB", keyCode + " " + event.getAction() + "");
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (keyboardEventListener != null) {
                keyboardEventListener.onClose(this);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public void setBackPressedListener(KeyboardEventListener listener) {
        keyboardEventListener = listener;
    }

    public interface KeyboardEventListener {
        void onClose(FontEditText FontEditText);
        void onOpen(FontEditText FontEditText);
    }
}