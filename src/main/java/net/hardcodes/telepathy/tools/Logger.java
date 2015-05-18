package net.hardcodes.telepathy.tools;

import android.content.SharedPreferences;
import android.os.Debug;
import android.preference.PreferenceManager;
import android.util.Log;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.Telepathy;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by MnQko on 5.5.2015 .
 */
public class Logger {

    public static final boolean DEBUG = true;
    private static final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Telepathy.getContext());

    public static void log(String tag, String message, Exception e) {
        if (DEBUG) {
            Log.e(tag, message, e);
        }
    }

    public static void log(String tag, String message) {
        if (DEBUG) {
            Log.d(tag, message);

            Date now = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            String timestamp = dateFormat.format(now);
            String currentLog = prefs.getString(Constants.PREFERENCE_DEBUG_LOG, "");
            if (currentLog.length() > 16384) {
                currentLog = currentLog.substring(2, currentLog.length());
                currentLog = currentLog.substring(0, currentLog.indexOf("\n"));
            }
            currentLog = currentLog.concat("\n" + timestamp + " " + tag + ": " + message);
            prefs.edit().putString(Constants.PREFERENCE_DEBUG_LOG, currentLog).apply();
        }
    }
}
