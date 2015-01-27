package net.hardcodes.telepathy;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import net.hardcodes.telepathy.tools.CodecUtils;
import net.hardcodes.telepathy.tools.ShellCommandExecutor;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class RemoteControlService extends Service {

    private static final String TAG = "StreamingServer";

    public WebSocket webSocket;

    private MediaCodec encoder = null;
    Thread encoderThread = null;

    private float bitrateRatio;
    int deviceWidth;
    int deviceHeight;
    Point resolution = new Point();
    long frameCount = 0;

    Handler mHandler;
    SharedPreferences preferences;

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
        if (intent != null && intent.getAction().equals("STOP")) {
            stopEncoder();
            stopForeground(true);
            stopSelf();
        }

        if (intent.getAction().equals("START")) {
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
            DisplayMetrics dm = new DisplayMetrics();
            Display mDisplay = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            mDisplay.getMetrics(dm);
            deviceWidth = dm.widthPixels;
            deviceHeight = dm.heightPixels;
            float resolutionRatio = Float.parseFloat(
                    preferences.getString(SettingsActivity.KEY_RESOLUTION_PREF, "0.25"));
            mDisplay.getRealSize(resolution);
            resolution.x = (int) (resolution.x * resolutionRatio);
            resolution.y = (int) (resolution.y * resolutionRatio);

            bitrateRatio = Float.parseFloat(preferences.getString(SettingsActivity.KEY_BITRATE_PREF, "1"));
            connect();

            mHandler = new Handler();
        }
        return START_NOT_STICKY;
    }

    private void connect() {
        String address = preferences.getString("server", "192.168.0.104:8021/tp");
        AsyncHttpClient.getDefaultInstance().websocket("ws://" + address, null, webSocketCallback);
    }

    private void stopEncoder() {
        if (encoder != null) {
            encoder.signalEndOfInputStream();
        }
        if (encoderThread != null) {
            encoderThread = null;
        }
    }

    private AsyncHttpClient.WebSocketConnectCallback webSocketCallback = new AsyncHttpClient.WebSocketConnectCallback() {

        @Override
        public void onCompleted(Exception ex, final WebSocket webSocket) {
            if (webSocket == null || ex != null) {
                showToast("Support server not available. Attempting to reconnect...");
                connect();
                return;
            } else {
                showToast("Connected to support server.");
            }

            RemoteControlService.this.webSocket = webSocket;

            String uid = preferences.getString("uid", "111");
            webSocket.send(TelepathyAPI.MESSAGE_LOGIN + uid);
            startPingPong();

            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    // TODO: Why does the socket disconnect when the remote control session is interrupted from the other end?
                    showToast("Disconnected from server. Reconnecting...");
                    connect();
                }
            });

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    Log.d("API", s);

                    if (s.startsWith(TelepathyAPI.MESSAGE_CONNECT)) {
                        String remoteUID = s.split(TelepathyAPI.MESSAGE_UID_DELIMITER)[1];
                        webSocket.send(TelepathyAPI.MESSAGE_CONNECT_ACCEPTED + remoteUID);
                        showToast("User " + remoteUID + " has connected.");
                        //Start rendering display on the surface and set up the encoder.
                        if (encoderThread == null) {
                            startDisplayManager();
                            encoderThread = new Thread(new EncoderWorker(), "Encoder Thread");
                            encoderThread.start();
                        }

                    } else if (s.startsWith(TelepathyAPI.MESSAGE_DISCONNECT)) {
                        stopEncoder();

                    } else if (s.startsWith(TelepathyAPI.MESSAGE_ERROR)) {
                        showToast("Server: " + s);
                        stopEncoder();

                    } else if (s.startsWith(TelepathyAPI.MESSAGE_INPUT)) {
                        String messagePayload = s.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
                        String[] parts = messagePayload.split(",");

                        if (parts.length < 2) {
                            return;
                        }
                        try {
                            float x = Float.parseFloat(parts[0]) * deviceWidth;
                            float y = Float.parseFloat(parts[1]) * deviceHeight;
                            ShellCommandExecutor.getInstance().runCommand("input tap " + x + " " + y);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    byteBufferList.recycle();
                }
            });
        }
    };

    private Surface createDisplaySurface() throws IOException {
        MediaFormat mMediaFormat = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE,
                resolution.x, resolution.y);
        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) (1024 * 1024 * bitrateRatio));
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        Log.i(TAG, "Starting encoder");
        encoder = MediaCodec.createEncoderByType(CodecUtils.MIME_TYPE);
        encoder.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        Surface surface = encoder.createInputSurface();

        encoder.start();
        return surface;
    }

    public void startDisplayManager() {
        DisplayManager mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Surface encoderInputSurface = null;
        try {
            encoderInputSurface = createDisplaySurface();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDisplayManager.createVirtualDisplay("ScreenRecorder", resolution.x, resolution.y, 50,
                encoderInputSurface, DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
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
                    e.printStackTrace();
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
                        e.printStackTrace();
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
                        e.printStackTrace();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        stopEncoder();
        webSocket.send(TelepathyAPI.MESSAGE_DISCONNECT);
        webSocket.send(TelepathyAPI.MESSAGE_LOGOUT);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showToast(final String message) {
        mHandler.post(new ToastRunnable(message));
    }

    private void startPingPong() {
        new Timer("keep_alive").scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (webSocket != null) {
                    webSocket.ping(TelepathyAPI.MESSAGE_HEARTBEAT);
                }
            }
        }, 0, 10 * 1000);
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