package net.hardcodes.telepathy;

import android.app.Application;
import android.content.Context;

/**
 * Created by StereoPor on 29.3.2015 г..
 */
public class Telepathy extends Application {

    private static Context context;

    public void onCreate(){
        super.onCreate();
        this.context = getApplicationContext();
    }

    public static Context getContext() {
        return context;
    }
}
