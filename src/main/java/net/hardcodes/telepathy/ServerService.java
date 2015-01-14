package net.hardcodes.telepathy;

import android.annotation.TargetApi;
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
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;

import net.hardcodes.telepathy.tools.CodecUtils;
import net.hardcodes.telepathy.tools.ShellCommandExecutor;
import net.hardcodes.telepathy.tools.Utils;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ServerService extends Service {

    private MediaCodec encoder = null;

    private static final String TAG = "StreamingServer";

    private int serverPort;
    private float bitrateRatio;

    private AsyncHttpServer server;
    private List<WebSocket> _sockets = new ArrayList<WebSocket>();

    long frameCount = 0;

    Thread encoderThread = null;

    Handler mHandler;

    SharedPreferences preferences;

    int deviceWidth;
    int deviceHeight;
    Point resolution = new Point();

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
        if (intent != null && intent.getAction() == "STOP") {
            if (encoder != null)
                encoder.signalEndOfInputStream();
            server.stop();
            server = null;
            stopForeground(true);
            stopSelf();
        }
        if (server == null && intent.getAction().equals("START")) {
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

            server = new AsyncHttpServer();
            server.websocket("/", null, websocketCallback);
            serverPort = Integer.parseInt(preferences.getString(SettingsActivity.KEY_PORT_PREF, "6000"));
            bitrateRatio = Float.parseFloat(preferences.getString(SettingsActivity.KEY_BITRATE_PREF, "1"));
            updateNotification("Streaming is live at");
            server.listen(serverPort);
            mHandler = new Handler();
        }
        return START_NOT_STICKY;
    }

    private AsyncHttpServer.WebSocketRequestCallback websocketCallback = new AsyncHttpServer.WebSocketRequestCallback() {

        @Override
        public void onConnected(final WebSocket webSocket, AsyncHttpServerRequest request) {
            _sockets.add(webSocket);
            showToast("Someone just connected");
            //Start rendering display on the surface and setting up the encoder
            if (encoderThread == null) {
                startDisplayManager();
                encoderThread = new Thread(new EncoderWorker(), "Encoder Thread");
                encoderThread.start();
            }
            //Use this to clean up any references to the websocket
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        if (ex != null)
                            ex.printStackTrace();
                    } finally {
                        _sockets.clear();
                        showToast("Removed");
                    }
                    showToast("Disconnected");
                }
            });

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    String[] parts = s.split(",");
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
            });

            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    byteBufferList.recycle();
                }
            });
        }
    };

    @TargetApi(19)
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

    @TargetApi(19)
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

    @TargetApi(19)
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
                    for (WebSocket socket : _sockets) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            socket.send(info.offset + "," + info.size + "," +
                                    info.presentationTimeUs + "," + info.flags + "," +
                                    resolution.x + "," + resolution.y);
                        } else {
                            socket.send(info.offset + "," + info.size + "," +
                                    info.presentationTimeUs + "," + info.flags);
                        }

                        byte[] b = new byte[info.size];
                        try {
                            encodedData.position(info.offset);
                            encodedData.get(b, info.offset, info.offset + info.size);
                            socket.send(b);
                        } catch (BufferUnderflowException e) {
                            e.printStackTrace();
                        }
                    }
                    encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    try {
                        encoder.releaseOutputBuffer(encoderStatus, false);
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
        super.onDestroy();
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showToast(final String message) {
        mHandler.post(new ToastRunnable(message));
    }


    private void updateNotification(String message) {
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction("STOP");
        PendingIntent stopServiceIntent = PendingIntent.getService(this, 0, intent, 0);
        Notification.Builder mBuilder =
                new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_media_stop, "Stop", stopServiceIntent)
                        .setContentTitle(message)
                        .setContentText(Utils.getIPAddress(true) + ":" + serverPort);
        startForeground(6000, mBuilder.build());
    }
}
