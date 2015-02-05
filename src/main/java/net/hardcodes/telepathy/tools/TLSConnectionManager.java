package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import com.koushikdutta.async.http.AsyncHttpClient;

import net.hardcodes.telepathy.R;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by MnQko on 1.2.2015 г..
 */
public class TLSConnectionManager {

    public static void connectToServer(Context context, AsyncHttpClient.WebSocketConnectCallback webSocketCallback) {

        boolean secureConnection = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("useTLS", false);
        String address = PreferenceManager.getDefaultSharedPreferences(context).getString("server", "97e4fm0tw8.worldsupport.info:8021/tp");
        String protocol = "ws://";

        if (secureConnection) {
           protocol = "wss://";
           SSLContext sslContext = null;
           TrustManager[] trustEveryone = new TrustManager[]{
                   new X509TrustManager() {
                       public void checkClientTrusted(X509Certificate[] chain, String authType) {
                       }

                       public void checkServerTrusted(X509Certificate[] chain, String authType) {
                       }

                       public X509Certificate[] getAcceptedIssuers() {
                           return new X509Certificate[]{};
                       }
                   }
           };

           try {
               KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
               KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

               ks.load(context.getResources().openRawResource(R.raw.test_512bit_keystore), "BCFFAAB67DF49E37C9E3DAD16A0F1A6F0F2BB93981D88BAC97CD7E293932E043".toCharArray());
               kmf.init(ks, "BCFFAAB67DF49E37C9E3DAD16A0F1A6F0F2BB93981D88BAC97CD7E293932E043".toCharArray());

               sslContext = SSLContext.getInstance("TLSv1.2");
               sslContext.init(kmf.getKeyManagers(), trustEveryone, null);
           } catch (Exception e) {
               Log.d("SSLCONFIG", e.toString(), e);
           }
           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setSSLContext(sslContext);
           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setTrustManagers(trustEveryone);
           //TODO Hostname validation should not be disabled!
           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setHostnameVerifier(new HostnameVerifier() {
               public boolean verify(String hostname, SSLSession session) {
                   return true;
               }
           });
           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setConnectAllAddresses(true);
       }
        AsyncHttpClient.getDefaultInstance().websocket(protocol + address, null, webSocketCallback);
    }
}
