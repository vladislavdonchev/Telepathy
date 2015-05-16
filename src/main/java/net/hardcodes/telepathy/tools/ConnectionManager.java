package net.hardcodes.telepathy.tools;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;

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
import net.hardcodes.telepathy.PingPongService;
import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.RemoteControlService;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.dialogs.ProgressDialog;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.model.User;

import java.security.KeyStore;
import java.security.Provider;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by vladislav.donchev on 1.2.2015 г..
 */
public class ConnectionManager implements ProviderInstaller.ProviderInstallListener {

    public static final int ERROR_CODE_NO_INTERNET_CONNECTION = 101;
    public static final int ERROR_CODE_TLS_CONFIG_FAILED = 401;
    public static final int ERROR_CODE_SERVER_UNAVAILABLE = 408;

    public static final String ACTION_CONNECTION_STATE_CHANGE = "connStateChange";

    private static ConnectionManager instance;
    private ProgressDialog progressDialog;

    private ConcurrentHashMap<Context, WebSocketConnectionListener> connectionListeners = new ConcurrentHashMap<>();

    private String serverAddress;
    private WebSocket webSocket;

    private boolean userLogin = false;
    private boolean connectionDrop = false;
    private boolean connectedAndAuthenticated = false;

    private void reportConnectionError(WebSocketConnectionListener connectionListener, int errorCode) {
        String errorMessage = "Unidentified error?!";
        switch (errorCode) {
            case ERROR_CODE_NO_INTERNET_CONNECTION:
                errorMessage = "No Internet connection available!";
                break;
            case ERROR_CODE_TLS_CONFIG_FAILED:
                errorMessage = "TLS configuration failed! Please update the application.";
                break;
            case ERROR_CODE_SERVER_UNAVAILABLE:
                errorMessage = "Server not available! Please try again later.";
                break;
        }
        Telepathy.showLongToast(errorMessage);

        if (connectionListener != null) {
            connectionListener.onError(errorCode);
        } else {
            for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                webSocketConnectionListener.onError(errorCode);
            }
        }
    }

    private AsyncHttpClient.WebSocketConnectCallback connectCallback = new AsyncHttpClient.WebSocketConnectCallback() {
        @Override
        public void onCompleted(Exception ex, WebSocket webSocket) {
            if (webSocket != null) {
                Logger.log("WS", "CONNECTED");
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
                        webSocketConnectionListener.onConnectionAcquired();
                    }
                }
            }

            if (ex != null || webSocket == null) {
                for (WebSocketConnectionListener webSocketConnectionListener : connectionListeners.values()) {
                    int errorCode = ERROR_CODE_SERVER_UNAVAILABLE;
                    if (NetworkUtil.getConnectivityStatus(Telepathy.getContext()) == NetworkUtil.NO_CONNECTIVITY) {
                        errorCode = ERROR_CODE_NO_INTERNET_CONNECTION;
                    }
                    reportConnectionError(webSocketConnectionListener, errorCode);
                    setConnectedAndAuthenticated(false);
                }

                try {
                    Logger.log("WS", ex.toString() + ": " + ex.getCause(), ex);
                    return;
                } catch (Exception e) {
                }
                Logger.log("WS", "SOCKET NULL");
            }
        }
    };
    private CompletedCallback closeCallback = new CompletedCallback() {
        @Override
        public void onCompleted(Exception ex) {
            if (ex != null) {
                Logger.log("WS", ex.toString(), ex);
            }
            Logger.log("WS", "SOCKET CLOSED");

            stopPingPong();
            setConnectedAndAuthenticated(false);

            if (NetworkUtil.getConnectivityStatus(Telepathy.getContext()) == NetworkUtil.NO_CONNECTIVITY) {
                Logger.log("WS", "INTERNET DIED");
                reportConnectionError(null, ERROR_CODE_NO_INTERNET_CONNECTION);
            } else if (userLogin) {
                Logger.log("WS", "CONNECTION DROP");
                connectionDrop = true;
                connect();
            } else {
                Logger.log("WS", "SERVER UNAVAILABLE");
                reportConnectionError(null, ERROR_CODE_SERVER_UNAVAILABLE);
            }
        }
    };
    private WebSocket.StringCallback stringCallback = new WebSocket.StringCallback() {
        @Override
        public void onStringAvailable(String s) {
            Logger.log("WS", "RECEIVE: " + s);
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
        progressDialog = new ProgressDialog(Telepathy.getContext(), "Configuring security provider...");
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
        Logger.log("WS", "SET CAA: " + connectedAndAuthenticated);
        this.connectedAndAuthenticated = connectedAndAuthenticated;
        Intent connectionStateChangeIntent = new Intent(ACTION_CONNECTION_STATE_CHANGE);
        Telepathy.getContext().sendBroadcast(connectionStateChangeIntent);
    }

    public interface WebSocketConnectionListener {

        void onConnectionAcquired();

        void onError(int errorCode);

        void onTextMessage(String message);

        void onBinaryMessage(ByteBufferList byteBufferList);
    }

    public void acquireConnection(Context context, WebSocketConnectionListener connectionListener) {
        connectionListeners.put(context, connectionListener);
        Logger.log("WS", "ADD LISTENER: " + context + " " + connectionListener + " TOTAL: " + connectionListeners.size());

        if (webSocket != null && webSocket.isOpen()) {
            connectionListener.onConnectionAcquired();
            return;
        }

        boolean secureConnection = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(Constants.PREFERENCE_USE_TLS, false);
        String address = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFERENCE_SERVER_ADDRESS, "telepathy.hardcodes.net:8021/tp");
        String protocol = "ws://";

        if (secureConnection) {
            protocol = "wss://";
            serverAddress = protocol + address;

            progressDialog.show();
            try {
                ProviderInstaller.installIfNeededAsync(Telepathy.getContext(), this);
            } catch (Exception e) {
                Logger.log("SSLCONFIG", e.toString(), e);
                reportConnectionError(connectionListener, ERROR_CODE_TLS_CONFIG_FAILED);
            }
        } else {
            serverAddress = protocol + address;
            connect();
        }
    }

    @Override
    public void onProviderInstalled() {
        progressDialog.hide();
        SSLContext sslContext;
        TrustManagerFactory tmf;

        try {
            tmf = TrustManagerFactory.getInstance("X509");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            String companyName = Telepathy.getContext().getString(R.string.company_name);
            ks.load(new FileCipher().readEncryptedFile(Telepathy.getContext().getAssets().open("font/unsteady_oversteer.ttf")),
                    Utils.sha256(companyName).toUpperCase().toCharArray());
            kmf.init(ks, Utils.sha256(companyName).toUpperCase().toCharArray());
            tmf.init(ks);

            sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            sslContext.getDefaultSSLParameters().setProtocols(new String[]{"TLSv1.2"});
            sslContext.createSSLEngine().setEnabledProtocols(new String[]{"TLSv1.2"});
        } catch (Exception e) {
            Logger.log("SSLCONFIG", e.toString(), e);
            reportConnectionError(null, ERROR_CODE_TLS_CONFIG_FAILED);
            return;
        }

        AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setSSLContext(sslContext);
        AsyncHttpClient.getDefaultInstance().getSSLSocketMiddleware().setTrustManagers(tmf.getTrustManagers());

        connect();
    }

    @Override
    public void onProviderInstallFailed(int i, Intent intent) {
        progressDialog.hide();
        Logger.log("SSLCONFIG", "Security provider installation failed: " + i);
        reportConnectionError(null, ERROR_CODE_TLS_CONFIG_FAILED);
    }

    private void connect() {
        Logger.log("WS", "CONNECTING TO: " + serverAddress);
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.close();
            webSocket = null;
        }
        AsyncHttpClient.getDefaultInstance().websocket(serverAddress, null, connectCallback);
    }

    public void registerAccount(User user) {
        Logger.log("WTF", "register" + new Gson().toJson(user));
        sendTextMessage(TelepathyAPI.MESSAGE_REGISTER + new Gson().toJson(user));
    }

    public void login(Context context) {
        Logger.log("WS", "LOGIN ATTEMPT CAA: " + connectedAndAuthenticated + " UL: " + userLogin);
        userLogin = true;
        if (!connectedAndAuthenticated) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String uid = preferences.getString(Constants.PREFERENCE_UID, "");
            String pass = preferences.getString(Constants.PREFERENCE_PASS, "");
            sendTextMessage(TelepathyAPI.MESSAGE_LOGIN + uid + TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER + Utils.sha256(pass));
        }
    }

    public void logout() {
        Logger.log("WS", "LOGOUT");
        userLogin = false;
        sendTextMessage(TelepathyAPI.MESSAGE_LOGOUT);
    }

    public void releaseConnection(Context context) {
        if (connectionListeners.containsKey(context)) {
            connectionListeners.remove(context);
            Logger.log("WS LISTENERS", "REMOVE: " + context + " " + connectionListeners.get(context) + " TOTAL: " + connectionListeners.size());
        }

        if (connectionListeners.size() == 0) {
            logout();
        }
    }

    public void sendTextMessage(String message) {
        if (webSocket != null && webSocket.isOpen()) {
            try {
                webSocket.send(message);
                Logger.log("WS", "SEND TEXT: " + message);
            } catch (Exception e) {
                Logger.log("WSSEND", e.toString(), e);
                Mint.logException(e);
            }
        }
    }

    public void sendBinaryMessage(byte[] message) {
        if (webSocket != null && webSocket.isOpen()) {
            try {
                webSocket.send(message);
            } catch (Exception e) {
                Logger.log("WSSEND", e.toString(), e);
                Mint.logException(e);
            }
        }
    }

    public void ping(String message) {
        webSocket.ping(message);
    }

    private void startPingPong() {
        Context context = Telepathy.getContext();
        Intent pingPongIntent = new Intent(context, PingPongService.class);
        pingPongIntent.setAction(PingPongService.ACTION_START);
        context.startService(pingPongIntent);
    }

    private void stopPingPong() {
        Context context = Telepathy.getContext();
        Intent pingPongIntent = new Intent(context, PingPongService.class);
        pingPongIntent.setAction(PingPongService.ACTION_STOP);
        context.startService(pingPongIntent);
        context.stopService(pingPongIntent);
    }
}