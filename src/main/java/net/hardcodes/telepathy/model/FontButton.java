package net.hardcodes.telepathy.model;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.Button;

import net.hardcodes.telepathy.R;

/**
 * Created by aleksandar dimitrov on 2/26/15.
 */
public class FontButton extends Button {

    public FontButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public FontButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FontButton(Context context) {
        super(context);
        init();
    }

    private void init() {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "font/forced_square.ttf");
        setTypeface(tf);
        this.setShadowLayer(8, 8, 8, R.color.text_shade_color);
    }
}