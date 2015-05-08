package net.hardcodes.telepathy;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import net.hardcodes.telepathy.dialogs.LoginDialog;
import net.hardcodes.telepathy.tools.Logger;

/**
 * Created by StereoPor on 29.3.2015 г..
 */
public class Telepathy extends Application {

    private static Context context;
    private static LoginDialog loginDialog;
    private static Handler uiHandler;

    public void onCreate(){
        super.onCreate();
        context = getApplicationContext();
        loginDialog = new LoginDialog(context);
        uiHandler = new Handler();
    }

    public static Context getContext() {
        return context;
    }

    public static void attemptLogin(final boolean previousAuthenticationFailed) {
        if (!loginDialog.isShowing() || previousAuthenticationFailed) {
            runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    loginDialog.show(previousAuthenticationFailed);
                }
            });
        }
    }

    public static void runOnUIThread(Runnable runnable) {
        uiHandler.post(runnable);
    }

    public static void runOnUIThread(Runnable runnable, long delay) {
        uiHandler.postDelayed(runnable, delay);
    }

    public static void showLongToast(String text) {
        showToast(text, Toast.LENGTH_LONG);
    }

    public static void showShortToast(String text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    private static void showToast(String text, int duration) {
        uiHandler.post(new ToastRunnable(text, duration));
    }

    private static class ToastRunnable implements Runnable {
        String text;
        private int duration;

        public ToastRunnable(String text, int duration) {
            this.text = text;
            this.duration = duration;
        }

        @Override
        public void run() {
            Toast.makeText(context, text, duration).show();
        }
    }
}
