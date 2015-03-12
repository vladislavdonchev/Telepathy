package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import net.hardcodes.telepathy.Constants;

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

    public static String getNetworkState(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if(info==null || !info.isConnected())
            return Constants.CONSTANT_NETWORK_NOT_CONNECTED;
        if(info.getType() == ConnectivityManager.TYPE_WIFI)
            return Constants.CONSTANT_NETWORK_WFI;
        if(info.getType() == ConnectivityManager.TYPE_MOBILE){
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return Constants.CONSTANT_NETWORK_2G;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return Constants.CONSTANT_NETWORK_3G;
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return Constants.CONSTANT_NETWORK_4G;
                default:
                    return Constants.CONSTANT_NETWORK_ERROR;
            }
        }
        return Constants.CONSTANT_NETWORK_ERROR;
    }
}