package net.hardcodes.telepathy.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.splunk.mint.Mint;

import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.dialogs.InstallUninstallDialog;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.RemoteControlService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;

public class Utils {

    public static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void writeInstallationDetailsToFile(Context context) {
        OutputStreamWriter outputStreamWriter = null;
        try {
            outputStreamWriter = new OutputStreamWriter(new FileOutputStream (new File(Environment.getExternalStorageDirectory(), InstallUninstallDialog.CONFIGURATION_FILE)));
            outputStreamWriter.write(context.getString(R.string.app_version));
            outputStreamWriter.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        } finally {
            if (outputStreamWriter != null) {
                try {
                    outputStreamWriter.close();
                } catch (IOException e) {
                    Log.e("FILE", "Error closing output stream writer...: " + e.toString());
                }
            }
        }
    }

    public static String getInstallationDetailsFromFile(Context context) {
        String version = "";
        InputStream inputStream = null;

        try {
            inputStream = new FileInputStream(new File(Environment.getExternalStorageDirectory(), InstallUninstallDialog.CONFIGURATION_FILE));

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null) {
                    stringBuilder.append(receiveString);
                }
                version = stringBuilder.toString();
            }
        } catch (FileNotFoundException e) {
            Log.e("FILE", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("FILE", "Could not read file: " + e.toString());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e("FILE", "Error closing input stream...: " + e.toString());
                }
            }
        }

        return version;
    }

    public static void deleteInstallationDetails() {
        File configFile = new File(Environment.getExternalStorageDirectory(), InstallUninstallDialog.CONFIGURATION_FILE);
        if (configFile != null && configFile.exists()) {
            configFile.delete();
        }
    }

    public static void startService() {
        Context context = Telepathy.getContext();
        Intent serverIntent = new Intent(context, RemoteControlService.class);
        serverIntent.setAction(RemoteControlService.ACTION_START);
        context.startService(serverIntent);
    }

    public static void stopService() {
        Context context = Telepathy.getContext();
        Intent serverIntent = new Intent(context, RemoteControlService.class);
        serverIntent.setAction(RemoteControlService.ACTION_STOP);
        context.startService(serverIntent);
        context.stopService(serverIntent);
    }

    public static boolean isServiceRunning(Class<?> serviceClass) {
        Context context = Telepathy.getContext();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static void setStringPref(Context context, String pref, String value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(pref, value);
        editor.commit();
    }

    public static void setBooleanPref(Context context, String pref, Boolean value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(pref, value);
        editor.commit();
    }

    public static void splunk(Context context) {
        if (TextUtils.isEmpty(Mint.getSessionId())) {
            Mint.initAndStartSession(context, "48f4134d");
        }
    }

    public static byte[] mergeByteArrays(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c= new byte[aLen+bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }
}
