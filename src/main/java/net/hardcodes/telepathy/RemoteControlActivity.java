package net.hardcodes.telepathy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import net.hardcodes.telepathy.tools.CodecUtils;
import net.hardcodes.telepathy.tools.TLSConnectionManager;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;


public class RemoteControlActivity extends Activity implements SurfaceHolder.Callback, View.OnTouchListener {

    private static final String TAG = "RemoteControlActivity";

    SurfaceView surfaceView;

    MediaCodec decoder;
    boolean decoderConfigured = false;
    ByteBuffer[] decoderInputBuffers = null;
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    long frameCount = 0;

    private WebSocket webSocket;

    String serverAddress;
    String remoteUID;

    int deviceWidth;
    int deviceHeight;
    Point videoResolution = new Point();
    private SharedPreferences preferences;
    private Timer pingPongTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        initDisplayMetrics();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        serverAddress = preferences.getString("server", "192.168.0.104:8021/tp");
        remoteUID = getIntent().getStringExtra(ConnectDialog.KEY_UID_EXTRA);

        surfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnTouchListener(this);
    }

    @Override
    protected void onResume() {
        hideSystemUI();
        super.onResume();
    }

    private void initDisplayMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        deviceWidth = dm.widthPixels;
        deviceHeight = dm.heightPixels;
    }


    @Override
    public void onBackPressed() {
        disconnect();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private void disconnect() {
        if (webSocket != null) {
            webSocket.send(TelepathyAPI.MESSAGE_DISBAND);
            webSocket.send(TelepathyAPI.MESSAGE_LOGOUT);
            webSocket.close();
            webSocket = null;
        }
    }

    private AsyncHttpClient.WebSocketConnectCallback websocketCallback = new AsyncHttpClient
            .WebSocketConnectCallback() {
        @Override
        public void onCompleted(Exception ex, final WebSocket webSocket) {
            if (ex != null) {
                Log.d("WSFAIL", ex.toString() + ": " + ex.getCause(), ex);
            }

            if (webSocket == null) {
                showToast("Server not available.");
                return;
            }

            RemoteControlActivity.this.webSocket = webSocket;

            String uid = preferences.getString("uid", "111");
            webSocket.send(TelepathyAPI.MESSAGE_LOGIN + uid);
            webSocket.send(TelepathyAPI.MESSAGE_BIND + remoteUID);
            startPingPong();

            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception e) {
                    stopPingPong();
                    finish();
                }
            });

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                public void onStringAvailable(String s) {
                    Log.d("API", s);

                    if (s.startsWith(TelepathyAPI.MESSAGE_BIND_ACCEPTED)) {
                        showToast("Remote controlling user " + remoteUID);
                    } else if (s.startsWith(TelepathyAPI.MESSAGE_BIND_FAILED)) {
                        showToast("User " + remoteUID + " not logged in. Please try again later.");
                        finish();
                    } else if (s.startsWith(TelepathyAPI.MESSAGE_ERROR)) {
                        showToast("Server: " + s);
                        finish();
                    } else if (s.startsWith(TelepathyAPI.MESSAGE_VIDEO_METADATA)) {
                        String messagePayload = s.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
                        String[] parts = messagePayload.split(",");

                        try {
                            info.set(Integer.parseInt(parts[0]),
                                    Integer.parseInt(parts[1]),
                                    Long.parseLong(parts[2]),
                                    Integer.parseInt(parts[3]));
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                videoResolution.x = Integer.parseInt(parts[4]);
                                videoResolution.y = Integer.parseInt(parts[5]);
                            }
                        } catch (NumberFormatException e) {
                            Log.d(TAG, e.toString(), e);
                            //TODO: Need to stop the decoder or to skip the current decoder loop
                            showToast(e.getMessage());
                        }
                    }
                }
            });

            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                    ByteBuffer b = byteBufferList.getAll();
                    //b.position(info.offset);
                    b.position(0);
                    //b.limit(info.offset + info.size);
                    b.limit(info.size);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        MediaFormat format =
                                MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE,
                                        videoResolution.x, videoResolution.y);
                        format.setByteBuffer("csd-0", b);
                        decoder.configure(format, surfaceView.getHolder().getSurface(), null, 0);
                        decoder.start();
                        byteBufferList.recycle();
                        decoderInputBuffers = decoder.getInputBuffers();
                        decoderConfigured = true;
                        return;
                    }
                    int inputBufIndex = decoder.dequeueInputBuffer(CodecUtils.TIMEOUT_USEC);
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputBuf.limit(info.offset + info.size);
                        byte[] buff = new byte[info.size];
                        b.get(buff, 0, info.size);
                        try {
                            inputBuf.put(buff);
                        } catch (BufferOverflowException e) {
                            showToast("Buffer Overflow = " + e.getMessage());
                            Log.d(TAG, "Input buff capacity = " + inputBuf.capacity() + " limit = " + inputBuf.limit() + " byte size = " + buff.length);
                            byteBufferList.recycle();
                            return;
                        }

                        inputBuf.rewind();
                        decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                                info.presentationTimeUs, 0 /*flags*/);
                    }
                    int decoderStatus = decoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        Log.d(TAG, "no output from decoder available");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // The storage associated with the direct ByteBuffer may already be unmapped,
                        // so attempting to access data through the old output buffer array could
                        // lead to a native crash.
                        Log.d(TAG, "decoder output buffers changed");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // this happens before the first frame is returned
                        MediaFormat decoderOutputFormat = decoder.getOutputFormat();
                        Log.d(TAG, "decoder output format changed: " + decoderOutputFormat);
                    } else if (decoderStatus < 0) {
                        //TODO: fail
                        showToast("Something wrong with the decoder. Need to stop everything.");
                    } else {
                        if (info.size == 0) {
                            Log.d(TAG, "got empty frame");
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "output EOS");
                        }
                        boolean doRender = (info.size != 0);
                        decoder.releaseOutputBuffer(decoderStatus, doRender /*render*/);
                    }
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
        }
    };

    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showToast(final String message) {
        RemoteControlActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(RemoteControlActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        try {
            decoder = MediaCodec.createDecoderByType(CodecUtils.MIME_TYPE);
            TLSConnectionManager.connectToServer(this, websocketCallback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        // TODO: Support the whole range of motion events.
        if (webSocket != null) {
            webSocket.send(TelepathyAPI.MESSAGE_INPUT + motionEvent.getX() / deviceWidth + "," + motionEvent.getY() / deviceHeight);
        }
        return false;
    }

    private void startPingPong() {
        pingPongTimer = new Timer("keep_alive");
        pingPongTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    webSocket.ping(TelepathyAPI.MESSAGE_HEARTBEAT);
                } catch (Exception e) {
                    Log.d("WEBSOCKPING", e.toString(), e);
                }
            }
        }, 0, 10 * 1000);
    }

    private void stopPingPong() {
        pingPongTimer.cancel();
        pingPongTimer.purge();
    }
}