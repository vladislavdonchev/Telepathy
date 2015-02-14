package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Created by MnQko on 14.2.2015 г..
 */
public class NetworkUtil {

    public final static int NO_CONNECTIVITY = -1;

    public static int getConnectivityStatus(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (null != activeNetwork) {
            return activeNetwork.getType();
        } else {
            return NO_CONNECTIVITY;
        }
    }
}