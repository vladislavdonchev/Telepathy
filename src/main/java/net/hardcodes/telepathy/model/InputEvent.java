package net.hardcodes.telepathy.model;

/**
 * Created by aleksandar.dimitrov on 15-2-4.
 */
public class InputEvent {
    public static final int IMPUT_EVENT_TYPE_BACK_BUTTON = 1;
    public static final int IMPUT_EVENT_TYPE_HOME_BUTTON = 2;
    public static final int IMPUT_EVENT_TYPE_RECENT_BUTTON = 3;
    public static final int IMPUT_EVENT_TYPE_LOCK_UNLOCK_BUTTON = 4;
    public static final int IMPUT_EVENT_TYPE_TOUCH = 5;
    public static final int IMPUT_EVENT_TYPE_SWIPE = 6;
    public static final int IMPUT_EVENT_TYPE_LONG_PRESS = 7;

    public static final int IMPUT_EVENT_FLING_DURATION = 200;
    public static final int IMPUT_EVENT_LONG_PRESS_DURATION = 700;

    private float toucEventX = 0;
    private float touchEventY = 0;


    private float toucEventX1 = 0;
    private float touchEventY1 = 0;
    private int imputType = 0;

    public int getImputType() {
        return imputType;
    }

    public void setImputType(int imputType) {
        this.imputType = imputType;
    }

    public float getTouchEventX() {
        return toucEventX;
    }

    public void setToucEventX(float toucEventX) {
        this.toucEventX = toucEventX;
    }

    public float getTouchEventY() {
        return touchEventY;
    }

    public void setTouchEventY(float touchEventY) {
        this.touchEventY = touchEventY;
    }

    public float getToucEventX1() {
        return toucEventX1;
    }

    public void setToucEventX1(float toucEventX1) {
        this.toucEventX1 = toucEventX1;
    }

    public float getTouchEventY1() {
        return touchEventY1;
    }

    public void setTouchEventY1(float touchEventY1) {
        this.touchEventY1 = touchEventY1;
    }

    public InputEvent() {

    }
}
