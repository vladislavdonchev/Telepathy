package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.security.ProviderInstaller;
import com.google.gson.Gson;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.splunk.mint.Mint;

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
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Created by vladislav.donchev on 1.2.2015 г..
 */
public class ConnectionManager {

    public static final String ACTION_CONNECTION_STATE_CHANGE = "connStateChange";

    private static ConnectionManager instance;

    private static ConcurrentHashMap<Context, WebSocketConnectionListener> connectionListeners = new ConcurrentHashMap<>();

    public WebSocket webSocket;
    private Timer pingPongTimer;
    private String serverAddress;

    private boolean userLogin = false;
    private boolean connectionDrop = false;
    private boolean connectedAndAuthenticated = false;

    private AsyncHttpClient.WebSocketConnectCallback connectCallback = new AsyncHttpClient.WebSocketConnectCallback() {
        @Override
        public void onCompleted(Exception ex, WebSocket webSocket) {
            if (webSocket != null) {
                webSocket.setClosedCallback(closeCallback);
                webSocket.setStringCallback(stringCallback);
                webSocket.setDataCallback(dataCallback);
                ConnectionManager.this.webSocket = webSocket;

                startPingPong();

                if (connectionDrop) {
                    connectionDrop = false;
                    login(Telepathy.getContext());
                } else {
                    for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                        webSocketConnectionListener.onConnect();
                    }
                }
            }

            if (ex != null || webSocket == null) {
                for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                    webSocketConnectionListener.onError(WebSocketConnectionListener.ERROR_CODE_SERVER_UNAVAILABLE);
                    setConnectedAndAuthenticated(false);
                }

                try {
                    Log.d("WSFAIL", ex.toString() + ": " + ex.getCause(), ex);
                    return;
                } catch (Exception e) {
                }
                Log.d("WSFAIL", "Socket NULL.");
            }
        }
    };
    private CompletedCallback closeCallback = new CompletedCallback() {
        @Override
        public void onCompleted(Exception ex) {
            if (ex != null) {
                Log.d("WSCLOSE", ex.toString(), ex);
            }
            Log.d("WSCLOSE", "Socket closed.");

            if (NetworkUtil.getConnectivityStatus(Telepathy.getContext()) == NetworkUtil.NO_CONNECTIVITY) {
                for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                    webSocketConnectionListener.onDisconnect();
                }
            } else if (userLogin) {
                connectionDrop = true;
                connect();
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
                    case TelepathyAPI.ERROR_USER_ID_TAKEN:
                        Telepathy.showLongToast("Account registration failed!");
                        break;
                    case TelepathyAPI.ERROR_USER_AUTHENTICATION_FAILED:
                        Telepathy.showLongToast("User authentication failed!");
                        Telepathy.attemptLogin(true);
                        setConnectedAndAuthenticated(false);
                        break;
                    case TelepathyAPI.ERROR_OTHER_END_HUNG_UP_UNEXPECTEDLY:
                        Telepathy.showLongToast("The connection has been interrupted unexpectedly.");
                        break;
                    case TelepathyAPI.ERROR_BIND_FAILED:
                        Telepathy.showLongToast("The user you are trying to reach is not logged in.");
                        break;
                    case TelepathyAPI.ERROR_OTHER_USER_BUSY:
                        Telepathy.showLongToast("The user you are trying to reach is busy.");
                        break;
                    case TelepathyAPI.ERROR_SERVER_OVERLOADED:
                        Telepathy.showLongToast("The server is overloaded. Please try again later.");
                        break;
                }
            }
            if (s.startsWith(TelepathyAPI.MESSAGE_LOGIN_SUCCESS)) {
                setConnectedAndAuthenticated(true);
                if (!Utils.isServiceRunning(RemoteControlService.class)) {
                    Utils.startService();
                }
            } else if (s.startsWith(TelepathyAPI.MESSAGE_LOGOUT_SUCCESS)) {
                setConnectedAndAuthenticated(false);
                if (Utils.isServiceRunning(RemoteControlService.class)) {
                    Utils.stopService();
                }
                if (webSocket != null && webSocket.isOpen()) {
                    webSocket.close();
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
            bb.recycle();
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
        Log.d("WS LISTENERS", "ADD: " + context + " " + connectionListener + " TOTAL: " + connectionListeners.size());

        if (webSocket != null && webSocket.isOpen()) {
            connectionListener.onConnect();
            return;
        }

        try {
            ProviderInstaller.installIfNeeded(context.getApplicationContext());
        } catch (Exception e) {
            Telepathy.showLongToast(e.getMessage());
            connectionListener.onDisconnect();
            return;
        }

        boolean secureConnection = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREFERENCE_USE_TLS, false);
        String address = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_SERVER_ADDRESS, "54.68.141.75:8021/tp");
        String protocol = "ws://";

        if (secureConnection) {
            protocol = "wss://";
            SSLContext sslContext = null;
            TrustManagerFactory tmf = null;

            try {
                tmf = TrustManagerFactory.getInstance("X509");
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                // TODO Key should not be saved in plain text.
                ks.load(new FileCipher().readEncryptedFile(context.getAssets().open("font/unsteady_oversteer.ttf")),
                        "C927F8D7624213BF8128B434DE471F1EA8F0EB7DD4AD82364689E7CFA759422E".toCharArray());
                kmf.init(ks, "C927F8D7624213BF8128B434DE471F1EA8F0EB7DD4AD82364689E7CFA759422E".toCharArray());
                tmf.init(ks);

                sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                sslContext.getDefaultSSLParameters().setProtocols(new String[]{"TLSv1.2"});
                sslContext.createSSLEngine().setEnabledProtocols(new String[]{"TLSv1.2"});
            } catch (Exception e) {
                Log.d("SSLCONFIG", e.toString(), e);
                Telepathy.showLongToast(e.getMessage());
                connectionListener.onDisconnect();
                return;
            }

            AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setSSLContext(sslContext);
            AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setTrustManagers(tmf.getTrustManagers());
            // TODO Hostname validation should not be disabled!
            AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setHostnameVerifier(new DodgyHostnameVerifier());
        }

        serverAddress = protocol + address;
        connect();
    }

    private void connect() {
        Log.d("WS", "connecting to " + serverAddress);
        AsyncHttpClient.getDefaultInstance().websocket(serverAddress, null, connectCallback);
    }

    public void registerAccount(User user) {
        sendTextMessage(TelepathyAPI.MESSAGE_REGISTER + new Gson().toJson(user));
    }

    public void login(Context context) {
        Log.d("CONN", "login CAA: " + connectedAndAuthenticated + " UL: " + userLogin);
        userLogin = true;
        if (!connectedAndAuthenticated) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String uid = preferences.getString(Constants.PREFERENCE_UID, "");
            String pass = preferences.getString(Constants.PREFERENCE_PASS, "");
            sendTextMessage(TelepathyAPI.MESSAGE_LOGIN + uid + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER + Utils.sha256(pass));
        }
    }

    public void logout() {
        Log.d("WS", "logout");
        userLogin = false;
        sendTextMessage(TelepathyAPI.MESSAGE_LOGOUT);
    }

    public void releaseConnection(Context context) {
        if (connectionListeners.containsKey(context)) {
            connectionListeners.remove(context);
            Log.d("WS LISTENERS", "REMOVE: " + context + " " + connectionListeners.get(context) + " TOTAL: " + connectionListeners.size());
        }

        if (connectionListeners.size() == 0) {
            logout();
        }
    }

    public void sendTextMessage(String message) {
        Log.d("WS", "send text -> " + message);
        if (webSocket != null && webSocket.isOpen()) {
            try {
                webSocket.send(message);
            } catch (Exception e) {
                Log.d("WSSEND", e.toString(), e);
                Mint.logException(e);
            }
        }
    }

    public void sendBinaryMessage(byte[] message) {
        if (webSocket != null && webSocket.isOpen()) {
            try {
                webSocket.send(message);
            } catch (Exception e) {
                Log.d("WSSEND", e.toString(), e);
                Mint.logException(e);
            }
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

    private class DodgyHostnameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}