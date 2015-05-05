package net.hardcodes.telepathy.activities;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.koushikdutta.async.ByteBufferList;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.dialogs.ConnectDialog;
import net.hardcodes.telepathy.model.InputEvent;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.tools.CodecUtils;
import net.hardcodes.telepathy.tools.ConnectionManager;

import org.w3c.dom.Text;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;


public class RemoteControlActivity extends Activity implements ConnectionManager.WebSocketConnectionListener, SurfaceHolder.Callback, GestureDetector.OnGestureListener, View.OnClickListener {

    private static final String TAG = "DECODER";

    private SurfaceView surfaceView;

    private MediaCodec decoder;
    private boolean decoderConfigured = false;
    private ByteBuffer[] decoderInputBuffers = null;
    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private String remoteUID;

    private int deviceWidth;
    private int deviceHeight;
    private Point videoResolution = new Point();

    private ImageButton buttonShowHideButtons;
    private LinearLayout buttonsContainer;
    private ImageButton buttonHome;
    private ImageButton buttonBack;
    private ImageButton buttonLockUnlock;
    private ImageButton buttonRecentApps;
    private GestureDetector mDetector;
    private CountDownTimer hideControlsTimer;

    private boolean bound = false;

    @Override
    public void onConnectionAcquired() {
        ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_BIND + remoteUID);
    }

    @Override
    public void onError(int errorCode) {
        bound = false;
        finish();
    }

    @Override
    public void onTextMessage(String message) {
        Log.d("API", message);

        if (message.startsWith(TelepathyAPI.MESSAGE_BIND_ACCEPTED)) {
            bound = true;
            showToast("Remote controlling user " + remoteUID);
        }
    }

    private void handleVideoMetadata(String metadata) {
        Log.d("VIDMETA", metadata);
        String messagePayload = metadata.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
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

    @Override
    public void onBinaryMessage(ByteBufferList byteArray) {
        ByteBuffer b = byteArray.getAll();

        byte[] metadataBytes = new byte[CodecUtils.VIDEO_META_MAX_LEN];
        b.get(metadataBytes);
        String metadataHolder = new String(metadataBytes);
        String metadata = metadataHolder.substring(0, metadataHolder.indexOf(";"));
        if (!TextUtils.isEmpty(metadata)) {
            handleVideoMetadata(metadata);
        }

        if (!b.hasRemaining()) {
            return;
        }

        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            MediaFormat format = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE,
                            videoResolution.x, videoResolution.y);
            format.setByteBuffer("csd-0", b);
            decoder.configure(format, surfaceView.getHolder().getSurface(), null, info.flags);
            decoder.start();
            decoderInputBuffers = decoder.getInputBuffers();
            decoderConfigured = true;
            return;
        }

        try {
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
                showToast("Decoder error.");
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
        } catch (Exception e) {
            Log.d(TAG, e.toString(), e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        initDisplayMetrics();

        remoteUID = getIntent().getStringExtra(ConnectDialog.KEY_UID_EXTRA);

        surfaceView = (SurfaceView) findViewById(R.id.main_surface_view);
        surfaceView.getHolder().addCallback(this);

        buttonsContainer = (LinearLayout) findViewById(R.id.buttons_container);

        buttonShowHideButtons = (ImageButton) findViewById(R.id.arrow_show_hide_buttons);
        buttonShowHideButtons.setOnClickListener(this);
        buttonHome = (ImageButton) findViewById(R.id.home_button);
        buttonHome.setOnClickListener(this);
        buttonBack = (ImageButton) findViewById(R.id.back_button);
        buttonBack.setOnClickListener(this);
        buttonLockUnlock = (ImageButton) findViewById(R.id.lock_unlock_button);
        buttonLockUnlock.setOnClickListener(this);
        buttonRecentApps = (ImageButton) findViewById(R.id.recent_apps_button);
        buttonRecentApps.setOnClickListener(this);

        mDetector = new GestureDetector(this, this);

        hideControlsTimer = new CountDownTimer(3000, 100) {
            public void onFinish() {
                hideButtonsContainer();
            }
            @Override
            public void onTick(long millisUntilFinished) {
            }
        };
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
    protected void onDestroy() {
        if (bound) {
            ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_DISBAND);
        }
        ConnectionManager.getInstance().releaseConnection(this);
        if (decoder != null) {
            decoder.stop();
            decoder.release();
        }
        super.onDestroy();
    }

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
                Toast.makeText(RemoteControlActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        try {
            decoder = MediaCodec.createDecoderByType(CodecUtils.MIME_TYPE);
            ConnectionManager.getInstance().acquireConnection(this, this);
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
    public void onClick(View v) {
        if (v.getId() == R.id.arrow_show_hide_buttons) {
            buttonsContainer.setVisibility(View.VISIBLE);
            buttonShowHideButtons.setVisibility(View.GONE);
        } else if (v.getId() == R.id.back_button) {
            sendInputAction(InputEvent.IMPUT_EVENT_TYPE_BACK_BUTTON, 0, 0, 0, 0);
        } else if (v.getId() == R.id.home_button) {
            sendInputAction(InputEvent.IMPUT_EVENT_TYPE_HOME_BUTTON, 0, 0, 0, 0);
        } else if (v.getId() == R.id.recent_apps_button) {
            sendInputAction(InputEvent.IMPUT_EVENT_TYPE_RECENT_BUTTON, 0, 0, 0, 0);
        } else if (v.getId() == R.id.lock_unlock_button) {
            sendInputAction(InputEvent.IMPUT_EVENT_TYPE_LOCK_UNLOCK_BUTTON, 0, 0, 0, 0);
        }

        hideControlsTimer.start();
    }

    private void sendInputAction(int eventType, float x, float y, float x1, float y1) {
        Gson gson = new Gson();
        InputEvent event = new InputEvent();
        event.setImputType(eventType);
        event.setToucEventX(x);
        event.setTouchEventY(y);
        event.setToucEventX1(x1);
        event.setTouchEventY1(y1);
        String eventJson = gson.toJson(event);
        ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_INPUT + eventJson);
    }

    private void hideButtonsContainer() {
        buttonsContainer.setVisibility(View.GONE);
        buttonShowHideButtons.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.mDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        sendInputAction(InputEvent.IMPUT_EVENT_TYPE_TOUCH, (e.getX() / deviceWidth), (e.getY() / deviceHeight), 0, 0);
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        sendInputAction(InputEvent.IMPUT_EVENT_TYPE_LONG_PRESS, (e.getX() / deviceWidth), (e.getY() / deviceHeight), 0, 0);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        sendInputAction(InputEvent.IMPUT_EVENT_TYPE_SWIPE, (e1.getX() / deviceWidth), (e1.getY() / deviceHeight), (e2.getX() / deviceWidth), (e2.getY() / deviceHeight));
        return false;
    }
}
