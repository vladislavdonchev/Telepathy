package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by aleksandar dimitrov on 2/26/15.
 */
public class FontTextView extends TextView {

    public FontTextView(Context context) {
        super(context);
        Typeface face= Typeface.createFromAsset(context.getAssets(), "font/forced_square.ttf");
        this.setTypeface(face);
    }

    public FontTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Typeface face=Typeface.createFromAsset(context.getAssets(), "font/forced_square.ttf");
        this.setTypeface(face);
    }

    public FontTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Typeface face=Typeface.createFromAsset(context.getAssets(), "font/forced_square.ttf");
        this.setTypeface(face);
    }

    protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
    }

}
