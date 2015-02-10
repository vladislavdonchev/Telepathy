package net.hardcodes.telepathy;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.gson.Gson;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import net.hardcodes.telepathy.model.InputEvent;
import net.hardcodes.telepathy.tools.CodecUtils;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.ShellCommandExecutor;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class RemoteControlService extends Service {

public static final String ACTION_START = "START";
public static final String ACTION_STOP = "STOP";

    private static final String TAG = "StreamingServer";
    public static final String VIRTUAL_DISPLAY_TAG = "ScreenRecorder";

    private SharedPreferences preferences;
    private Handler toastHandler;
    KeyguardManager myKM;
    KeyguardManager.KeyguardLock kl;
    public WebSocket webSocket;
    private Timer pingPongTimer;

    private DisplayManager displayManager;
    private Surface encoderInputSurface = null;
    private VirtualDisplay virtualDisplay = null;
    private MediaCodec encoder = null;
    private Thread encoderThread = null;

    private float bitrateRatio;
    private int deviceWidth;
    private int deviceHeight;
    private Point resolution = new Point();

    private boolean running = false;

    private class ToastRunnable implements Runnable {
        String mText;

        public ToastRunnable(String text) {
            mText = text;
        }

        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), mText, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction().equals(ACTION_START)) {
                running = true;
                preferences = PreferenceManager.getDefaultSharedPreferences(this);
                displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                ConnectionManager.connectToServer(this, webSocketCallback);
                toastHandler = new Handler();
                myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                kl = myKM.newKeyguardLock("MyKeyguardLock");
            }

            if (intent.getAction().equals(ACTION_STOP)) {
                running = false;
                logout();
            }
        }
        return START_NOT_STICKY;
    }

    public void startEncodingVirtualDisplay() {
        initDisplayParameters();

        try {
            encoderInputSurface = createDisplaySurface();
        } catch (IOException e) {
            Log.d("ENCODER", e.toString(), e);
        }
        virtualDisplay = displayManager.createVirtualDisplay(VIRTUAL_DISPLAY_TAG, resolution.x, resolution.y, 50,
                encoderInputSurface, DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        encoderThread = new Thread(new EncoderWorker(), "Encoder Thread");
        encoderThread.start();
    }

    private Surface createDisplaySurface() throws IOException {
        MediaFormat mMediaFormat = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE,
                resolution.x, resolution.y);

        TelephonyManager telephonyManager;
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        int currentNetworkType = telephonyManager.getNetworkType();
        boolean isNetwork2G = currentNetworkType < TelephonyManager.NETWORK_TYPE_UMTS && currentNetworkType > TelephonyManager.NETWORK_TYPE_UNKNOWN;

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (isNetwork2G && !wifi.isConnected()) {
            bitrateRatio = 0.03125f;
            showToast("2G connection detected - reducing support video stream quality.");
        } else {
            bitrateRatio = Float.parseFloat(preferences.getString(SettingsActivity.KEY_BITRATE_PREF, "1"));
        }

        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) (1024 * 1024 * bitrateRatio));
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 5);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mMediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, 5);

        Log.i(TAG, "Starting encoder");
        encoder = MediaCodec.createEncoderByType(CodecUtils.MIME_TYPE);
        encoder.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        Surface surface = encoder.createInputSurface();

        encoder.start();
        return surface;
    }

    private void initDisplayParameters() {
        DisplayMetrics dm = new DisplayMetrics();
        Display mDisplay = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mDisplay.getMetrics(dm);
        deviceWidth = dm.widthPixels;
        deviceHeight = dm.heightPixels;
        float resolutionRatio = Float.parseFloat(preferences.getString(SettingsActivity.KEY_RESOLUTION_PREF, "0.25"));
        mDisplay.getRealSize(resolution);
        resolution.x = (int) (resolution.x * resolutionRatio);
        resolution.y = (int) (resolution.y * resolutionRatio);
    }

    private void stopEncodingVirtualDisplay() {
        try {
            if (encoder != null) {
                encoder.signalEndOfInputStream();
            }
            if (encoderThread != null) {
                encoderThread = null;
            }

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

            if (encoderInputSurface != null) {
                encoderInputSurface.release();
                encoderInputSurface = null;
            }
        } catch (Exception e) {
            Log.e("SERVICE", e.toString(), e);
        }
    }

    private void reconnectAfterError(String errorMessage) {
        showToast(errorMessage);
        stopEncodingVirtualDisplay();
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
        }
        ConnectionManager.connectToServer(this, webSocketCallback);
    }

    private AsyncHttpClient.WebSocketConnectCallback webSocketCallback = new AsyncHttpClient.WebSocketConnectCallback() {

        @Override
        public void onCompleted(Exception ex, final WebSocket webSocket) {
            if (ex != null) {
                Log.d("WSFAIL", ex.toString() + ": " + ex.getCause(), ex);
            }

            if (webSocket == null) {
                if (running) {
                    reconnectAfterError("Support server not available. Attempting to reconnect in 20 seconds...");
                } else {
                    showToast("Support service stopped.");
                }
                return;
            } else {
                showToast("Connected to support server.");
            }

            if (RemoteControlService.this.webSocket != null) {
                RemoteControlService.this.webSocket.close();
            }

            String uid = preferences.getString("uid", "111");
            webSocket.send(TelepathyAPI.MESSAGE_LOGIN + uid);
            startPingPong();

            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    if (!running) {
                        showToast("Support service stopped.");
                        return;
                    }

                    if (ex != null) {
                        Log.d("WSCLOSE", ex.toString(), ex);
                    }
                    stopPingPong();
                    // TODO: Why does the socket disconnect when the remote control session is interrupted from the other end?
                    reconnectAfterError("Disconnected from support server. Reconnecting in 20 seconds...");
                }
            });

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    Log.d("API", s);

                    if (s.startsWith(TelepathyAPI.MESSAGE_BIND)) {
                        String remoteUID = s.split(TelepathyAPI.MESSAGE_UID_DELIMITER)[1];
                        webSocket.send(TelepathyAPI.MESSAGE_BIND_ACCEPTED + remoteUID);
                        showToast("User " + remoteUID + " has connected.");
                        //Start rendering display on the surface and set up the encoder.
                        if (encoderThread == null) {
                            startEncodingVirtualDisplay();
                        }

                    } else if (s.startsWith(TelepathyAPI.MESSAGE_DISBAND)) {
                        stopEncodingVirtualDisplay();

                    } else if (s.startsWith(TelepathyAPI.MESSAGE_ERROR)) {
                        showToast("Server: " + s);
                        stopEncodingVirtualDisplay();

                    } else if (s.startsWith(TelepathyAPI.MESSAGE_INPUT)) {
                        Gson gson = new Gson();
                        String messagePayload = s.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
                        InputEvent inputEventObject = gson.fromJson(messagePayload, InputEvent.class);
                        decodeInputEvent(inputEventObject);
                    }
                }
            });

            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    byteBufferList.recycle();
                }
            });

            webSocket.setEndCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    if (ex == null) {
                        Log.d("WSEND", "END?!");
                    } else {
                        Log.d("WSEND", ex.toString(), ex);
                    }
                }
            });

            RemoteControlService.this.webSocket = webSocket;
        }
    };

    private void decodeInputEvent(InputEvent event) {
        switch (event.getImputType()) {
            case InputEvent.IMPUT_EVENT_TYPE_BACK_BUTTON:
                try {
                    ShellCommandExecutor.getInstance().runCommand("input keyevent 4");
                } catch (Exception e) {
                    Log.d("ENCODER", e.toString(), e);
                }
                break;
            case InputEvent.IMPUT_EVENT_TYPE_HOME_BUTTON:
                try {
                    ShellCommandExecutor.getInstance().runCommand("input keyevent 3");
                } catch (Exception e) {
                    Log.d("ENCODER", e.toString(), e);
                }
                break;
            case InputEvent.IMPUT_EVENT_TYPE_RECENT_BUTTON:
                try {
                    ShellCommandExecutor.getInstance().runCommand("input keyevent 187");
                } catch (Exception e) {
                    Log.d("ENCODER", e.toString(), e);
                }
                break;
            case InputEvent.IMPUT_EVENT_TYPE_LOCK_UNLOCK_BUTTON:
                if (myKM.inKeyguardRestrictedInputMode()) {
                    kl.disableKeyguard();
                    myKM.exitKeyguardSecurely(null);
                    try {
                        ShellCommandExecutor.getInstance().runCommand("input keyevent 26");
                    } catch (Exception e) {
                        Log.d("ENCODER", e.toString(), e);
                    }
                } else {
                    kl.reenableKeyguard();
                    try {
                        ShellCommandExecutor.getInstance().runCommand("input keyevent 26");
                    } catch (Exception e) {
                        Log.d("ENCODER", e.toString(), e);
                    }
                }
                break;
            case InputEvent.IMPUT_EVENT_TYPE_TOUCH:
                try {
                    float x = event.getToucEventX() * deviceWidth;
                    float y = event.getTouchEventY() * deviceHeight;
                    ShellCommandExecutor.getInstance().runCommand("input tap " + x + " " + y);
                } catch (Exception e) {
                    Log.d("ENCODER", e.toString(), e);
                }
                break;
            case InputEvent.IMPUT_EVENT_TYPE_LONG_PRESS:
                try {
                    float x = event.getToucEventX() * deviceWidth;
                    float y = event.getTouchEventY() * deviceHeight;
                    ShellCommandExecutor.getInstance().runCommand("input swipe " + x + " " + y + " " + x + " " + y + " " + InputEvent.IMPUT_EVENT_LONG_PRESS_DURATION);
                } catch (Exception e) {
                    Log.d("ENCODER", e.toString(), e);
                }
                break;
            case InputEvent.IMPUT_EVENT_TYPE_SWIPE:
                try {
                    float x = event.getToucEventX() * deviceWidth;
                    float y = event.getTouchEventY() * deviceHeight;
                    float x1 = event.getToucEventX1() * deviceWidth;
                    float y1 = event.getTouchEventY1() * deviceHeight;
                    ShellCommandExecutor.getInstance().runCommand("input swipe " + x + " " + y + " " + x1 + " " + y1 + " " + InputEvent.IMPUT_EVENT_FLING_DURATION);
                } catch (Exception e) {
                    Log.d("ENCODER", e.toString(), e);
                }
                break;
        }
    }


    private class EncoderWorker implements Runnable {

        @Override
        public void run() {
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();

            boolean encoderDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!encoderDone) {
                int encoderStatus;
                try {
                    encoderStatus = encoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
                } catch (IllegalStateException e) {
                    Log.d("ENCODER", e.toString(), e);
                    break;
                }

                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    //Log.d(TAG, "no output from encoder available");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // not expected for an encoder
                    MediaFormat newFormat = encoder.getOutputFormat();
                    Log.d(TAG, "encoder output format changed: " + newFormat);
                } else if (encoderStatus < 0) {
                    break;
                } else {
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.d(TAG, "NULL... Breaking!");
                        return;
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        webSocket.send(TelepathyAPI.MESSAGE_VIDEO_METADATA + info.offset + "," + info.size + "," +
                                info.presentationTimeUs + "," + info.flags + "," +
                                resolution.x + "," + resolution.y);
                    } else {
                        webSocket.send(TelepathyAPI.MESSAGE_VIDEO_METADATA + info.offset + "," + info.size + "," +
                                info.presentationTimeUs + "," + info.flags);
                    }

                    byte[] b = new byte[info.size];
                    try {
                        encodedData.position(info.offset);
                        encodedData.get(b, info.offset, info.offset + info.size);
                        webSocket.send(b);
                    } catch (BufferUnderflowException e) {
                        Log.d("ENCODER", e.toString(), e);
                    }

                    encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    try {
                        encoder.releaseOutputBuffer(encoderStatus, false);
                        if (encoderDone) {
                            encoder.stop();
                            encoder.release();
                            encoder = null;
                        }
                    } catch (IllegalStateException e) {
                        Log.d("ENCODER", e.toString(), e);
                        continue;
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        if (running) {
            logout();
        }
        super.onDestroy();
    }

    private void logout() {
        if (webSocket != null && webSocket.isOpen()) {
            webSocket.send(TelepathyAPI.MESSAGE_DISBAND);
            webSocket.send(TelepathyAPI.MESSAGE_LOGOUT);
        }
        stopEncodingVirtualDisplay();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showToast(final String message) {
        toastHandler.post(new ToastRunnable(message));
    }


    private void startPingPong() {
        pingPongTimer = new Timer("keep_alive");
        pingPongTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (webSocket != null) {
                    try {
                        webSocket.ping(TelepathyAPI.MESSAGE_HEARTBEAT);
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

    private void updateNotification(String message) {
        Intent intent = new Intent(this, RemoteControlService.class);
        intent.setAction("STOP");
        PendingIntent stopServiceIntent = PendingIntent.getService(this, 0, intent, 0);
        Notification.Builder mBuilder =
                new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_media_stop, "Stop", stopServiceIntent)
                        .setContentTitle(message);
        startForeground(6000, mBuilder.build());
    }
}
