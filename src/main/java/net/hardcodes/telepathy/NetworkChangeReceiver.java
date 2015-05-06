package net.hardcodes.telepathy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Logger;
import net.hardcodes.telepathy.tools.NetworkUtil;

/**
 * Created by vladislav.donchev on 14.2.2015 г..
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        int status = NetworkUtil.getConnectivityStatus(context);
        Logger.log("NETLISTENER", status + "");

        boolean isServiceAutostartEnabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Constants.PREFERENCE_AUTOSTART_SERVICE, false);
        boolean isConnectedAndAuthenticated = ConnectionManager.getInstance().isConnectedAndAuthenticated();

        if (status != NetworkUtil.NO_CONNECTIVITY && isServiceAutostartEnabled && !isConnectedAndAuthenticated) {
            Telepathy.attemptLogin(false);
        }
    }
}
