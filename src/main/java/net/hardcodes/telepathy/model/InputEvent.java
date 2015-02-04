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

    private float toucEventX = 0;
    private float touchEventY = 0;
    private int imputType = 0;

    public int getImputType() {
        return imputType;
    }

    public void setImputType(int imputType) {
        this.imputType = imputType;
    }

    public float getToucEventX() {
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


    public InputEvent() {

    }
}
