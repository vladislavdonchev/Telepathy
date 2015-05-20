package net.hardcodes.telepathy.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.TextView;

import net.hardcodes.telepathy.R;

import java.lang.reflect.Field;

/**
 * Created by aleksandar dimitrov on 2/26/15.
 */
public class FontTextView extends TextView {

    public FontTextView(Context context) {
        super(context);
        init();
    }

    public FontTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FontTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        Typeface face = Typeface.createFromAsset(getContext().getAssets(), "font/forced_square.ttf");
        this.setTypeface(face);
        this.setShadowLayer(8, 8, 8, R.color.text_shade_color);
        setSelected(true);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

}
