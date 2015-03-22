package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.model.TelepathyAPI;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by MnQko on 1.2.2015 г..
 */
public class ConnectionManager {

    private static ConnectionManager instance;
    private SharedPreferences preferences;

    private static ConcurrentHashMap<Context, WebSocketConnectionListener> connectionListeners = new ConcurrentHashMap<>();

    public WebSocket webSocket;
    private Timer pingPongTimer;

    private AsyncHttpClient.WebSocketConnectCallback connectCallback = new AsyncHttpClient.WebSocketConnectCallback() {
        @Override
        public void onCompleted(Exception ex, WebSocket webSocket) {
            if (ex != null) {
                Log.d("WSFAIL", ex.toString() + ": " + ex.getCause(), ex);
            }

            if (webSocket == null) {
                for (WebSocketConnectionListener webSocketConnectionListener: connectionListeners.values()) {
                    webSocketConnectionListener.onError(WebSocketConnectionListener.ERROR_CODE_SERVER_UNAVAILABLE);
                }
                Log.d("WSFAIL", "Socket NULL.");
            } else {
                webSocket.setClosedCallback(closeCallback);
                webSocket.setStringCallback(stringCallback);
                webSocket.setDataCallback(dataCallback);
                ConnectionManager.this.webSocket = webSocket;

                login();
                startPingPong();

                for (WebSocketConnectionListener webSocketConnectionListener: connectionListeners.values()) {
                    webSocketConnectionListener.onConnect();
                }
            }
        }
    };
    private CompletedCallback closeCallback = new CompletedCallback() {
        @Override
        public void onCompleted(Exception ex) {
            if (ex != null) {
                Log.d("WSCLOSE", ex.toString(), ex);
            }

            for (WebSocketConnectionListener webSocketConnectionListener: connectionListeners.values()) {
                webSocketConnectionListener.onDisconnect();
            }
            stopPingPong();
        }
    };
    private WebSocket.StringCallback stringCallback = new WebSocket.StringCallback() {
        @Override
        public void onStringAvailable(String s) {
            for (WebSocketConnectionListener webSocketConnectionListener: connectionListeners.values()) {
                webSocketConnectionListener.onTextMessage(s);
            }
        }
    };
    private DataCallback dataCallback = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            for (WebSocketConnectionListener webSocketConnectionListener: connectionListeners.values()) {
                webSocketConnectionListener.onBinaryMessage(bb);
            }
        }
    };

    private ConnectionManager(){
    }

    public static ConnectionManager getInstance(){
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public interface WebSocketConnectionListener {
        public static final int ERROR_CODE_SERVER_UNAVAILABLE = 0;
        public static final int ERROR_CODE_SERVER_CONNECTION_LOST = 1;

        public void onConnect();
        public void onError(int errorCode);
        public void onTextMessage(String message);
        public void onBinaryMessage(ByteBufferList byteBufferList);
        public void onDisconnect();
    }

    public void acquireConnection(Context context, WebSocketConnectionListener connectionListener) {
        connectionListeners.put(context, connectionListener);

        if (webSocket != null && webSocket.isOpen()) {
            connectionListener.onConnect();
            return;
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        boolean secureConnection = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("useTLS", false);
        String address = PreferenceManager.getDefaultSharedPreferences(context).getString("server", "97e4fm0tw8.worldsupport.info:8021/tp");
        String protocol = "ws://";

        if (secureConnection) {
           protocol = "wss://";
           SSLContext sslContext = null;
           TrustManager[] trustEveryone = new TrustManager[] { new DodgyTrustManager() };

           try {
               KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
               KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

               // TODO Key should not be saved in plain text.
               ks.load(context.getResources().openRawResource(R.raw.test_512bit_keystore), "BCFFAAB67DF49E37C9E3DAD16A0F1A6F0F2BB93981D88BAC97CD7E293932E043".toCharArray());
               kmf.init(ks, "BCFFAAB67DF49E37C9E3DAD16A0F1A6F0F2BB93981D88BAC97CD7E293932E043".toCharArray());

               sslContext = SSLContext.getInstance("TLSv1.2");
               sslContext.init(kmf.getKeyManagers(), trustEveryone, null);
           } catch (Exception e) {
               Log.d("SSLCONFIG", e.toString(), e);
           }

           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setSSLContext(sslContext);
           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setTrustManagers(trustEveryone);
           // TODO Hostname validation should not be disabled!
           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setHostnameVerifier(new DodgyHostnameVerifier());
           AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setConnectAllAddresses(true);
       }

       AsyncHttpClient.getDefaultInstance().websocket(protocol + address, null, connectCallback);
    }

    private void login() {
        String uid = preferences.getString("uid", "111");
        webSocket.send(TelepathyAPI.MESSAGE_LOGIN + uid);
    }

    public void releaseConnection(Context context) {
        sendTextMessage(TelepathyAPI.MESSAGE_DISBAND);
        // Logout if no other services / activities use the connection.
        if (webSocket != null && webSocket.isOpen() && connectionListeners.size() == 1) {
            webSocket.send(TelepathyAPI.MESSAGE_LOGOUT);
        }
        unregisterListener(context);
    }

    public void unregisterListener(Context context) {
        connectionListeners.remove(context);
    }

    public void sendTextMessage(String message) {
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.send(message);
        }
    }

    public void sendBinaryMessage(byte[] message) {
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.send(message);
        }
    }

    private void startPingPong() {
        pingPongTimer = new Timer("keep_alive");
        pingPongTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (webSocket != null) {
                    try {
                        webSocket.ping(TelepathyAPI.MESSAGE_HEARTBEAT);
                        Log.d("WEBSOCKPING", "ping");
                    } catch (Exception e) {
                        Log.d("WEBSOCKPING", e.toString(), e);
                    }
                }
            }
        }, 0, 10 * 1000);
    }

    private void stopPingPong() {
        pingPongTimer.cancel();
        pingPongTimer.purge();
    }

    private class DodgyTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }

    private class DodgyHostnameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}