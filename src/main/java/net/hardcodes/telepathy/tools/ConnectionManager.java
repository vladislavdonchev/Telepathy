package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.RemoteControlService;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.model.User;

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

    public static final String ACTION_CONNECTION_STATE_CHANGE = "connStateChange";

    private static ConnectionManager instance;

    private static ConcurrentHashMap<Context, WebSocketConnectionListener> connectionListeners = new ConcurrentHashMap<>();

    public WebSocket webSocket;
    private Timer pingPongTimer;

    private boolean connectedAndAuthenticated = false;

    private AsyncHttpClient.WebSocketConnectCallback connectCallback = new AsyncHttpClient.WebSocketConnectCallback() {
        @Override
        public void onCompleted(Exception ex, WebSocket webSocket) {
            if (ex != null) {
                Log.d("WSFAIL", ex.toString() + ": " + ex.getCause(), ex);
            }

            if (webSocket == null) {
                for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                    webSocketConnectionListener.onError(WebSocketConnectionListener.ERROR_CODE_SERVER_UNAVAILABLE);
                    setConnectedAndAuthenticated(false);
                }
                Log.d("WSFAIL", "Socket NULL.");
            } else {
                webSocket.setClosedCallback(closeCallback);
                webSocket.setStringCallback(stringCallback);
                webSocket.setDataCallback(dataCallback);
                ConnectionManager.this.webSocket = webSocket;

                startPingPong();

                for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
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

            for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                webSocketConnectionListener.onDisconnect();
            }
            stopPingPong();
            setConnectedAndAuthenticated(false);
        }
    };
    private WebSocket.StringCallback stringCallback = new WebSocket.StringCallback() {
        @Override
        public void onStringAvailable(String s) {
            Log.d("WS", "receive -> " + s);
            boolean isError = s.startsWith(TelepathyAPI.MESSAGE_ERROR);
            int errorCode = -1;
            if (isError) {
                errorCode = Integer.parseInt(s.substring(s.length() - 1));
                switch (errorCode) {
                    case TelepathyAPI.ERROR_USER_AUTHENTICATION_FAILED:
                        Telepathy.showLongToast("User authentication failed!");
                        Telepathy.attemptLogin(true);
                        setConnectedAndAuthenticated(false);
                        break;
                    case TelepathyAPI.ERROR_USER_ID_TAKEN:
                        Telepathy.showLongToast("Account registration failed!");
                        break;
                    case TelepathyAPI.ERROR_SERVER_OVERLOADED:
                        Telepathy.showLongToast("The server is overloaded. Please try again later.");
                        break;
                    case TelepathyAPI.ERROR_OTHER_END_HUNG_UP_UNEXPECTEDLY:
                        Telepathy.showLongToast("The connection has been interrupted unexpectedly.");
                        break;
                }
            }
            if (s.startsWith(TelepathyAPI.MESSAGE_LOGIN_SUCCESS)) {
                setConnectedAndAuthenticated(true);
                if (!Utils.isServiceRunning(RemoteControlService.class)) {
                    Utils.startService();
                }
            }
            for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                Log.d("WS", "redirectToListener: " + webSocketConnectionListener + " -> " + s);
                if (isError) {
                    webSocketConnectionListener.onError(errorCode);
                } else {
                    webSocketConnectionListener.onTextMessage(s);
                }
            }
        }
    };
    private DataCallback dataCallback = new DataCallback() {
        @Override
        public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
            for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                webSocketConnectionListener.onBinaryMessage(bb);
            }
        }
    };

    private ConnectionManager() {
    }

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public boolean isConnectedAndAuthenticated() {
        return connectedAndAuthenticated;
    }

    private void setConnectedAndAuthenticated(boolean connectedAndAuthenticated) {
        Log.d("WS", "set caa: " + connectedAndAuthenticated);
        this.connectedAndAuthenticated = connectedAndAuthenticated;
        Intent connectionStateChangeIntent = new Intent(ACTION_CONNECTION_STATE_CHANGE);
        Telepathy.getContext().sendBroadcast(connectionStateChangeIntent);
    }

    public interface WebSocketConnectionListener {
        public static final int ERROR_CODE_SERVER_UNAVAILABLE = 100;

        public void onConnect();

        public void onError(int errorCode);

        public void onTextMessage(String message);

        public void onBinaryMessage(ByteBufferList byteBufferList);

        public void onDisconnect();
    }

    public void acquireConnection(Context context, WebSocketConnectionListener connectionListener) {
        connectionListeners.put(context, connectionListener);
        Log.d("WS LISTENERS", "ADD: " + context + " " + connectionListener);

        if (webSocket != null && webSocket.isOpen()) {
            connectionListener.onConnect();
            return;
        }

        boolean secureConnection = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREFERENCE_USE_TLS, false);
        String address = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_SERVER_ADDRESS, "46.238.53.83:8021/tp");
        String protocol = "ws://";

        if (secureConnection) {
            protocol = "wss://";
            SSLContext sslContext = null;
            TrustManager[] trustEveryone = new TrustManager[]{new DodgyTrustManager()};

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

        Log.d("WS", "connecting to " + protocol + address);
        AsyncHttpClient.getDefaultInstance().websocket(protocol + address, null, connectCallback);
    }


    public void registerAccount(User user) {
        sendTextMessage(TelepathyAPI.MESSAGE_REGISTER + new Gson().toJson(user));
    }

    public void login(Context context) {
        Log.d("CONN", "login caa: " + connectedAndAuthenticated);
        if (!connectedAndAuthenticated) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String uid = preferences.getString(Constants.PREFERENCE_UID, "");
            String pass = preferences.getString(Constants.PREFERENCE_PASS, "");
            sendTextMessage(TelepathyAPI.MESSAGE_LOGIN + uid + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER + Utils.sha256(pass));
        }
    }

    public void logout() {
        Log.d("WS", "logout");
        sendTextMessage(TelepathyAPI.MESSAGE_DISBAND);
        // Logout if no other services / activities use the connection.
        sendTextMessage(TelepathyAPI.MESSAGE_LOGOUT);
        setConnectedAndAuthenticated(false);
    }

    public void releaseConnection(Context context) {
        if (connectionListeners.containsKey(context)) {
            Log.d("WS LISTENERS", "REMOVE: " + context + " " + connectionListeners.get(context));
            connectionListeners.remove(context);
        }

        if (connectionListeners.size() == 0) {
            logout();
            webSocket.close();
        }
    }

    public void sendTextMessage(String message) {
        Log.d("WS", "send text -> " + message);
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
        Log.d("WS", "start ping");
        if (pingPongTimer != null) {
            stopPingPong();
        }
        pingPongTimer = new Timer("keep_alive");
        pingPongTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (webSocket != null) {
                    try {
                        webSocket.ping(TelepathyAPI.MESSAGE_HEARTBEAT);
                        Log.d("WS", "ping");
                    } catch (Exception e) {
                        Log.d("WS", e.toString(), e);
                    }
                }
            }
        }, 0, 10 * 1000);
    }

    private void stopPingPong() {
        Log.d("WS", "stop ping");
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

    private class ToastRunnable implements Runnable {
        String mText;

        public ToastRunnable(String text) {
            mText = text;
        }

        @Override
        public void run() {
            Toast.makeText(Telepathy.getContext(), mText, Toast.LENGTH_SHORT).show();
        }
    }
}