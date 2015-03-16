package net.hardcodes.telepathy.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

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

public class Utils {

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

    public static void toggleService(Context context) {
        if (isServiceRunning(context, RemoteControlService.class)) {
            stopService(context);
        } else {
            startService(context);
        }
    }

    public static void startService(Context context) {
        Intent serverIntent = new Intent(context, RemoteControlService.class);
        serverIntent.setAction(RemoteControlService.ACTION_START);
        context.startService(serverIntent);
    }

    public static void stopService(Context context) {
        Intent serverIntent = new Intent(context, RemoteControlService.class);
        serverIntent.setAction(RemoteControlService.ACTION_STOP);
        context.startService(serverIntent);
        context.stopService(serverIntent);
    }

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
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
}
