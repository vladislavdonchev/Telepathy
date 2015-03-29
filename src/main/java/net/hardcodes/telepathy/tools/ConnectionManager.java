package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.dialogs.LoginDialog;
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

    public static final String ACTION_CONNECTION_STATE_CHANGE = "connStateChange";

    private static ConnectionManager instance;
    private static Handler uiHandler;
    private SharedPreferences preferences;

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

                login();
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
            for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                if (s.startsWith(TelepathyAPI.MESSAGE_ERROR)) {
                    int errorCode = Integer.parseInt(s.substring(s.length() - 1));
                    webSocketConnectionListener.onError(errorCode);

                    switch (errorCode) {
                        case TelepathyAPI.ERROR_USER_AUTHENTICATION_FAILED:
                            showToast("User authentication failed!");
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    new LoginDialog(Telepathy.getContext(), true).show();
                                }
                            });
                            break;
                        case TelepathyAPI.ERROR_USER_ID_TAKEN:
                            showToast("An user with this name already exists!");
                            break;
                        case TelepathyAPI.ERROR_SERVER_OVERLOADED:
                            showToast("The server is overloaded. Please try again later.");
                            break;
                        case TelepathyAPI.ERROR_OTHER_END_HUNG_UP_UNEXPECTEDLY:
                            showToast("The connection has been interrupted unexpectedly.");
                            break;
                    }
                } else if (s.startsWith(TelepathyAPI.MESSAGE_LOGIN_SUCCESS)) {
                    setConnectedAndAuthenticated(true);
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
            uiHandler = new Handler();
        }
        return instance;
    }

    public boolean isConnectedAndAuthenticated() {
        return connectedAndAuthenticated;
    }

    private void setConnectedAndAuthenticated(boolean connectedAndAuthenticated) {
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

        if (webSocket != null && webSocket.isOpen()) {
            connectionListener.onConnect();
            return;
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        String uid = preferences.getString(Constants.PREFERENCE_UID, "");
        if (TextUtils.isEmpty(uid)) {
            new LoginDialog(context).show();
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

        AsyncHttpClient.getDefaultInstance().websocket(protocol + address, null, connectCallback);
    }

    private void login() {
        String uid = preferences.getString(Constants.PREFERENCE_UID, "");
        String pass = preferences.getString(Constants.PREFERENCE_PASS, "");
        sendTextMessage(TelepathyAPI.MESSAGE_LOGIN + uid + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER + pass);
    }

    public void releaseConnection(Context context) {
        logout(false);
        unregisterListener(context);
    }

    public void reconnect(final Context context) {
        if (webSocket != null && webSocket.isOpen()) {
            Utils.stopService(context);
        }
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Utils.startService(context);
            }
        }, 1000);
    }

    private void logout(boolean forceful) {
        sendTextMessage(TelepathyAPI.MESSAGE_DISBAND);
        // Logout if no other services / activities use the connection.
        if (forceful || connectionListeners.size() == 1) {
            sendTextMessage(TelepathyAPI.MESSAGE_LOGOUT);
        }
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

    private void showToast(String message) {
        uiHandler.post(new ToastRunnable(message));
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