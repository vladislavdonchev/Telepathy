package net.hardcodes.telepathy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.hardcodes.telepathy.tools.NetworkUtil;
import net.hardcodes.telepathy.tools.Utils;

/**
 * Created by MnQko on 14.2.2015 г..
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        int status = NetworkUtil.getConnectivityStatus(context);
        boolean isServiceRunnning = Utils.isServiceRunning(context, RemoteControlService.class);
        Log.d("NETLISTENER", status + "");
        if (status != NetworkUtil.NO_CONNECTIVITY) {
            if (!isServiceRunnning) {
                Utils.startService(context);
            }
        } else {
            if (isServiceRunnning) {
                Utils.stopService(context);
            }
        }
    }
}
