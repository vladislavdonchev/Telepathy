package net.hardcodes.telepathy.model;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

import net.hardcodes.telepathy.R;

/**
 * Created by aleksandar dimitrov on 2/26/15.
 */
public class FontTextView extends TextView {

    public FontTextView(Context context) {
        super(context);
        Typeface face = Typeface.createFromAsset(context.getAssets(), "font/forced_square.ttf");
        this.setTypeface(face);
        this.setShadowLayer(8, 8, 8, R.color.text_shade_color);

    }

    public FontTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Typeface face = Typeface.createFromAsset(context.getAssets(), "font/forced_square.ttf");
        this.setTypeface(face);
        this.setShadowLayer(8, 8, 8, R.color.text_shade_color);
    }

    public FontTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Typeface face = Typeface.createFromAsset(context.getAssets(), "font/forced_square.ttf");
        this.setTypeface(face);
        this.setShadowLayer(8, 8, 8, R.color.text_shade_color);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

}
