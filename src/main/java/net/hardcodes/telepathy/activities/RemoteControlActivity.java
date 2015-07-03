package net.hardcodes.telepathy.activities;

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.koushikdutta.async.ByteBufferList;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.Telepathy;
import net.hardcodes.telepathy.dialogs.BaseDialog;
import net.hardcodes.telepathy.dialogs.ConnectDialog;
import net.hardcodes.telepathy.model.InputEvent;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.tools.CodecUtils;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Logger;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;


public class RemoteControlActivity extends Activity implements ConnectionManager.WebSocketConnectionListener,
        SurfaceHolder.Callback, GestureDetector.OnGestureListener, View.OnClickListener {

    private static final String TAG = "DECODER";

    private SurfaceView surfaceView;
    private ImageView loadingView;
    private boolean surfaceViewReady;

    private MediaCodec decoder;
    private ByteBuffer[] decoderInputBuffers = null;
    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private String remoteUID;

    private Point screenResolution = new Point();
    private Point streamResolution = new Point();

    private ImageButton buttonShowHideButtons;
    private LinearLayout buttonsContainer;
    private ImageButton buttonHome;
    private ImageButton buttonBack;
    private ImageButton buttonLockUnlock;
    private ImageButton buttonRecentApps;

    private GestureDetector gestureDetector;
    private CountDownTimer hideControlsTimer;

    private BaseDialog exitConfirmationDialog;

    @Override
    public void onConnectionAcquired() {
        ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_BIND + remoteUID);
    }

    @Override
    public void onError(int errorCode) {
        finish();
    }

    @Override
    public void onTextMessage(String message) {
        Logger.log("API", message);

        if (message.startsWith(TelepathyAPI.MESSAGE_BIND_ACCEPTED)) {
            showToast("Remote controlling user " + remoteUID + ".");
            Telepathy.runOnUIThread(hideLoadingViewRunnable);
        } else if (message.startsWith(TelepathyAPI.MESSAGE_BIND_REJECTED)) {
            showToast("Connection request denied by user " + remoteUID + ".");
            finish();
        } else if (message.startsWith(TelepathyAPI.MESSAGE_DISBAND)) {
            showToast("Connection interrupted by user " + remoteUID + ".");
            finish();
        }
    }

    private Runnable hideLoadingViewRunnable = new Runnable() {
        @Override
        public void run() {
            loadingView.setVisibility(View.INVISIBLE);
            buttonShowHideButtons.setVisibility(View.VISIBLE);
        }
    };

    private void handleVideoMetadata(String metadata) {
        Logger.log("VIDMETA", metadata);
        String messagePayload = metadata.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
        String[] parts = messagePayload.split(",");

        try {
            info.set(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Long.parseLong(parts[2]),
                    Integer.parseInt(parts[3]));
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                streamResolution.x = Integer.parseInt(parts[4]);
                streamResolution.y = Integer.parseInt(parts[5]);
            }
        } catch (NumberFormatException e) {
            Logger.log(TAG, e.toString(), e);
            //TODO: Need to stop the decoder or to skip the current decoder loop
            showToast(e.getMessage());
        }
    }

    @Override
    public void onBinaryMessage(ByteBufferList byteArray) {
        if (!surfaceViewReady) {
            return;
        }

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

        boolean isInitialConfigurationPending = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
        if (isInitialConfigurationPending) {
            configureDecoder(b);
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
                    Logger.log(TAG, "Input buff capacity = " + inputBuf.capacity() + " limit = " + inputBuf.limit() + " byte size = " + buff.length);
                    return;
                }

                inputBuf.rewind();
                decoder.queueInputBuffer(inputBufIndex, 0, info.size,
                        info.presentationTimeUs, 0 /*flags*/);
            }
            int decoderStatus = decoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                Logger.log(TAG, "no output from decoder available");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // The storage associated with the direct ByteBuffer may already be unmapped,
                // so attempting to access data through the old output buffer array could
                // lead to a native crash.
                Logger.log(TAG, "decoder output buffers changed");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // this happens before the first frame is returned
                MediaFormat decoderOutputFormat = decoder.getOutputFormat();
                Logger.log(TAG, "decoder output format changed: " + decoderOutputFormat);
            } else if (decoderStatus < 0) {
                //TODO: fail
                showToast("Decoder error.");
            } else {
                if (info.size == 0) {
                    Logger.log(TAG, "got empty frame");
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Logger.log(TAG, "output EOS");
                }
                boolean doRender = (info.size != 0);
                decoder.releaseOutputBuffer(decoderStatus, doRender /*render*/);
            }
        } catch (Exception e) {
            Logger.log(TAG, e.toString(), e);
        }
    }

    private void configureDecoder(ByteBuffer b) {
        MediaFormat format = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE, streamResolution.x, streamResolution.y);
        format.setByteBuffer("csd-0", b);
        decoder.configure(format, surfaceView.getHolder().getSurface(), null, info.flags);
        decoder.start();
        decoderInputBuffers = decoder.getInputBuffers();
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

        gestureDetector = new GestureDetector(this, this);

        hideControlsTimer = new CountDownTimer(3000, 100) {
            public void onFinish() {
                hideButtonsContainer();
            }

            @Override
            public void onTick(long millisUntilFinished) {
            }
        };

        exitConfirmationDialog = new BaseDialog(this);
        exitConfirmationDialog.setup("Exit Confirmation", "Are you sure you want to end the session?", "yes", "no", this);

        loadingView = (ImageView) findViewById(R.id.main_loading_view);
        ObjectAnimator anim = (ObjectAnimator) AnimatorInflater.loadAnimator(this, R.animator.flipper);
        anim.setTarget(loadingView);
        anim.setDuration(2000);
        anim.setRepeatCount(ObjectAnimator.INFINITE);
        anim.start();
        hideSystemUI();
    }

    @Override
    protected void onResume() {
        hideSystemUI();
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        exitConfirmationDialog.show();
    }

    private void initDisplayMetrics() {
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        display.getSize(screenResolution);
        Logger.log("RES_A", screenResolution.x + " " + screenResolution.y);
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
        Logger.log("DECODER", "surfaceCreated");
        surfaceViewReady = true;

        try {
            decoder = MediaCodec.createDecoderByType(CodecUtils.MIME_TYPE);
        } catch (IOException e) {
            Logger.log("DECODER", e.getMessage(), e);
        }

        ConnectionManager.getInstance().acquireConnection(this, this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
        Logger.log("DECODER", "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Logger.log("DECODER", "surfaceDestroyed");
        surfaceViewReady = false;

        ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_DISBAND);

        if (decoder != null) {
            decoder.stop();
            decoder.release();
        }
        surfaceViewReady = false;

        if (loadingView != null) {
            loadingView.setVisibility(View.VISIBLE);
            buttonShowHideButtons.setVisibility(View.INVISIBLE);
        }

        ConnectionManager.getInstance().releaseConnection(this);
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
        } else if (v.getId() == R.id.view_dialog_base_button_left) {
            super.onBackPressed();
        } else if (v.getId() == R.id.view_dialog_base_button_right) {
            exitConfirmationDialog.dismiss();
        }
        hideSystemUI();

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
        hideSystemUI();
        this.gestureDetector.onTouchEvent(event);
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
        hideSystemUI();
        sendInputAction(InputEvent.IMPUT_EVENT_TYPE_TOUCH, (e.getRawX() / screenResolution.x), (e.getRawY() / screenResolution.y), 0, 0);
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        sendInputAction(InputEvent.IMPUT_EVENT_TYPE_LONG_PRESS, (e.getRawX() / screenResolution.x), (e.getRawY() / screenResolution.y), 0, 0);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        sendInputAction(InputEvent.IMPUT_EVENT_TYPE_SWIPE, (e1.getRawX() / screenResolution.x), (e1.getRawY() / screenResolution.y), (e2.getRawX() / screenResolution.x), (e2.getRawY() / screenResolution.y));
        return false;
    }
}
