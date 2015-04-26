package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.google.gson.Gson;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.providers.grizzly.GrizzlyAsyncHttpProvider;
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketByteListener;
import com.ning.http.client.ws.WebSocketTextListener;
import com.ning.http.client.ws.WebSocketUpgradeHandler;
import com.splunk.mint.Mint;

import net.hardcodes.telepathy.Constants;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.RemoteControlService;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.model.User;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by MnQko on 1.2.2015 г..
 */
public class ConnectionManager implements WebSocketTextListener, WebSocketByteListener {

    public static final String ACTION_CONNECTION_STATE_CHANGE = "connStateChange";

    private static ConnectionManager instance;

    private AsyncHttpClient asyncHttpClient;
    private WebSocket webSocket;
    private Timer pingPongTimer;

    private boolean connectedAndAuthenticated = false;

    private static ConcurrentHashMap<Context, WebSocketConnectionListener> connectionListeners = new ConcurrentHashMap<>();

    private final BlockingQueue<String> textMessageQueue = new ArrayBlockingQueue<String>(1024);
    private final BlockingQueue<byte[]> binaryMessageQueue = new ArrayBlockingQueue<byte[]>(1024);
    private final BlockingQueue<byte[]> pingMessageQueue = new ArrayBlockingQueue<byte[]>(1024);

    private boolean isMessagingAlive = false;
    private Thread messagingThread = null;
    private Runnable messagingThreadRunnable = new Runnable() {
        @Override
        public void run() {

            while (isMessagingAlive) {
                try {
                    if (textMessageQueue.element() != null) {
                        String textMessage = null;
                        try {
                            textMessage = textMessageQueue.take();
                        } catch (InterruptedException e) {
                        }

                        Log.d("WS", "send text -> " + textMessage);
                        if (webSocket != null && webSocket.isOpen()) {
                            try {
                                webSocket.sendMessage(textMessage);
                            } catch (Exception e) {
                                Log.d("WSSEND", e.toString(), e);
                                Mint.logException(e);
                            }
                        }
                    }
                } catch (NoSuchElementException e) {
                }

                try {
                    if (binaryMessageQueue.element() != null) {
                        byte[] binaryMessage = null;
                        try {
                            binaryMessage = binaryMessageQueue.take();
                        } catch (InterruptedException e) {
                        }

                        if (webSocket != null && webSocket.isOpen()) {
                            try {
                                webSocket.sendMessage(binaryMessage);
                            } catch (Exception e) {
                                Log.d("WSSEND", e.toString(), e);
                                Mint.logException(e);
                            }
                        }
                    }
                } catch (NoSuchElementException e) {
                }

                try {
                    if (pingMessageQueue.element() != null) {
                        byte[] pingMessage = null;
                        try {
                            pingMessage = pingMessageQueue.take();
                        } catch (InterruptedException e) {
                        }

                        if (webSocket != null && webSocket.isOpen()) {
                            try {
                                webSocket.sendPing(pingMessage);
                                Log.d("WS", "ping");
                            } catch (Exception e) {
                                Log.d("WS", e.toString(), e);
                            }
                        }
                    }
                } catch (NoSuchElementException e) {
                }
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

    @Override
    public void onOpen(WebSocket webSocket) {
        if (webSocket == null) {
            for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                webSocketConnectionListener.onError(WebSocketConnectionListener.ERROR_CODE_SERVER_UNAVAILABLE);
                setConnectedAndAuthenticated(false);
            }
            Log.d("WSFAIL", "Socket NULL.");
        } else {
            ConnectionManager.this.webSocket = webSocket;

            isMessagingAlive = true;
            messagingThread = new Thread(messagingThreadRunnable);
            messagingThread.start();

            startPingPong();

            for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                webSocketConnectionListener.onConnect();
            }
        }
    }

    @Override
    public void onClose(WebSocket webSocket) {
        Log.d("WSCLOSE", "Socket closed.");

        isMessagingAlive = false;
        messagingThread = null;

        for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
            webSocketConnectionListener.onDisconnect();
        }

        stopPingPong();
        setConnectedAndAuthenticated(false);
        asyncHttpClient.close();
        asyncHttpClient = null;
    }

    @Override
    public void onError(Throwable throwable) {
        Log.d("WSERROR", throwable.getMessage(), throwable);
    }

    @Override
    public void onMessage(byte[] bytes) {
        for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
            webSocketConnectionListener.onBinaryMessage(bytes);
        }
    }

    @Override
    public void onMessage(String s) {
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

    public interface WebSocketConnectionListener {
        public static final int ERROR_CODE_SERVER_UNAVAILABLE = 100;

        public void onConnect();

        public void onError(int errorCode);

        public void onTextMessage(String message);

        public void onBinaryMessage(byte[] byteArray);

        public void onDisconnect();
    }

    public void acquireConnection(Context context, final WebSocketConnectionListener connectionListener) {
        connectionListeners.put(context, connectionListener);
        Log.d("WS LISTENERS", "ADD: " + context + " " + connectionListener + " TOTAL: " + connectionListeners.size());

        if (webSocket != null && webSocket.isOpen()) {
            connectionListener.onConnect();
            return;
        }

        boolean secureConnection = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREFERENCE_USE_TLS, false);
        String address = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_SERVER_ADDRESS, "46.238.53.83:8021/tp");
        String protocol = "ws://";

        try {
            ProviderInstaller.installIfNeeded(context.getApplicationContext());
        } catch (Exception e) {
            Telepathy.showLongToast(e.getMessage());
            connectionListener.onDisconnect();
            return;
        }
        AsyncHttpClientConfig.Builder asyncHttpClientConfigBuilder = new AsyncHttpClientConfig.Builder();//.setAcceptAnyCertificate(true);

        if (secureConnection) {
            protocol = "wss://";
            SSLContext sslContext = null;
            TrustManager[] trustEveryone = new TrustManager[]{new DodgyTrustManager()};

            try {
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                // TODO Key should not be saved in plain text.
                ks.load(context.getResources().openRawResource(R.raw.hardcodes2), "hardcodesnet@gmail.com".toCharArray());
                kmf.init(ks, "hardcodesnet@gmail.com".toCharArray());

                sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(kmf.getKeyManagers(), trustEveryone, null);
            } catch (Exception e) {
                Log.d("SSLCONFIG", e.toString(), e);
                Telepathy.showLongToast(e.getMessage());
                connectionListener.onDisconnect();
                return;
            }

            // TODO Hostname validation should not be disabled!
            asyncHttpClientConfigBuilder.setSSLContext(sslContext).setHostnameVerifier(new DodgyHostnameVerifier())
                    .setEnabledProtocols(new String[]{"TLSv1.2"});
        }

        final String serverAddress = protocol + address;
        Log.d("WS", "connecting to " + serverAddress);
        AsyncHttpClientConfig asyncHttpClientConfig = asyncHttpClientConfigBuilder.build();
        final AsyncHttpClient asyncHttpClient =
                new AsyncHttpClient(asyncHttpClientConfig);
        final WebSocketUpgradeHandler handler = new WebSocketUpgradeHandler.Builder().addWebSocketListener(this).build();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    webSocket = asyncHttpClient.prepareGet(serverAddress).execute(handler).get();
                } catch (Exception e) {
                    Log.d("WS", e.toString(), e);
                    Telepathy.showLongToast(e.getMessage());
                    connectionListener.onDisconnect();
                    return;
                }
            }
        }).start();

        this.asyncHttpClient = asyncHttpClient;
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
            connectionListeners.remove(context);
            Log.d("WS LISTENERS", "REMOVE: " + context + " " + connectionListeners.get(context) + " TOTAL: " + connectionListeners.size());
        }

        if (connectionListeners.size() == 0) {
            logout();
            if (webSocket != null && webSocket.isOpen()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        webSocket.close();
                    }
                }).start();
            }
        }
    }

    public void sendTextMessage(String message) {
        textMessageQueue.offer(message);
    }

    public void sendBinaryMessage(byte[] message) {
        binaryMessageQueue.offer(message);
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
                    pingMessageQueue.offer(TelepathyAPI.MESSAGE_HEARTBEAT.getBytes());
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
}