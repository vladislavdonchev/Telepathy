package net.hardcodes.telepathy.model;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.RadioButton;

/**
 * Created by aleksandardimitrov on 3/4/15.
 */
public class FontRadioButton extends RadioButton {

    public FontRadioButton(Context context) {
        super(context);
        init();
    }

    public FontRadioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FontRadioButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "font/forced_square.ttf");
        setTypeface(tf);
    }
    /**
     * Fix for putting the drawable in the center
     * notice that we put the background color of the drawable to transparent
     *
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}