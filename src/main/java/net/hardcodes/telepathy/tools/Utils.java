package net.hardcodes.telepathy.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import net.hardcodes.telepathy.InstallUninstallDialog;
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
        Intent startServerIntent = new Intent(context, RemoteControlService.class);
        if ( isServiceRunning(context, RemoteControlService.class)) {
            startServerIntent.setAction(RemoteControlService.ACTION_STOP);
        } else {
            startServerIntent.setAction(RemoteControlService.ACTION_START);
        }
        context.startService(startServerIntent);
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

    public static String sha256(String base) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch(Exception ex){
            throw new RuntimeException(ex);
        }
    }
}
