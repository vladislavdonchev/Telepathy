package net.hardcodes.telepathy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.koushikdutta.async.Util;

import net.hardcodes.telepathy.tools.Utils;

/**
 * Created by MnQko on 9.2.2015 г..
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent){
        try{
            Utils.toggleService(context);
            Log.i(TAG, "Starting service...");
        }catch(Exception e){
            Log.e(TAG, e.toString(), e);
        }
    }
}