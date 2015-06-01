package net.hardcodes.telepathy;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
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
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import com.google.gson.Gson;
import com.koushikdutta.async.ByteBufferList;

import net.hardcodes.telepathy.dialogs.BaseDialog;
import net.hardcodes.telepathy.model.InputEvent;
import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.tools.CodecUtils;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Logger;
import net.hardcodes.telepathy.tools.NetworkUtil;
import net.hardcodes.telepathy.tools.ShellCommandExecutor;
import net.hardcodes.telepathy.tools.Utils;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class RemoteControlService extends Service implements ConnectionManager.WebSocketConnectionListener, View.OnClickListener {

    public static final String ACTION_SERVICE_STATE_CHANGED = "stateChanged";

    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_DISBAND = "DISBAND";

    public static final String EXTRA_ACTIVITY_RESULT = "activity_result";
    public static final String EXTRA_ACTIVITY_RESULT_INTENT = "activity_result_intent";
    public static final int NOTIFICATION_ID = 6000;

    private int activityResult;
    private Intent activityResultIntent;
    private static final String VIRTUAL_DISPLAY_TAG = "ScreenRecorder";
    private static final String TAG = "StreamingServer";
    private KeyguardManager myKM;
    private KeyguardManager.KeyguardLock kl;
    private SharedPreferences preferences;

    private MediaProjection mediaProjection;
    private DisplayManager displayManager;
    private Surface encoderInputSurface;
    private VirtualDisplay virtualDisplay;

    private MediaCodec encoder;
    private Thread encoderThread;

    private float bitrateRatio;
    private Point screenResolution = new Point();
    private Point streamResolution = new Point();

    private String remoteUID;

    private final static int PERMISSION_NOT_SET = -1;
    private final static int PERMISSION_DENIED = 1;
    private final static int PERMISSION_GRANTED = 0;

    private BaseDialog permissionConfirmationDialog;
    private String lastPermissionRequest;
    private InputEvent lastScreenlockEvent;
    private int allowRemoteControl = PERMISSION_NOT_SET;
    private int allowScreernlockControl = PERMISSION_NOT_SET;

    @Override
    public void onConnectionAcquired() {
        if (!ConnectionManager.getInstance().isConnectedAndAuthenticated()) {
            Telepathy.attemptLogin(false);
        }
    }

    @Override
    public void onError(int errorCode) {
        stopEncodingVirtualDisplay();
        switch (errorCode) {
            case ConnectionManager.ERROR_CODE_SERVER_UNAVAILABLE:
            case ConnectionManager.ERROR_CODE_TLS_CONFIG_FAILED:
                stopSelf();
                break;
        }
    }

    @Override
    public void onTextMessage(String message) {
        Logger.log("API", message);

        if (message.startsWith(TelepathyAPI.MESSAGE_BIND)) {
            remoteUID = message.split(TelepathyAPI.MESSAGE_UID_DELIMITER)[1];
            switch (preferences.getString(Constants.PREFERENCE_CONNECTION_RQ, Constants.CONSTANT_STRING_PROMPT)) {
                case Constants.CONSTANT_STRING_PROMPT:
                    lastPermissionRequest = Constants.PREFERENCE_CONNECTION_RQ;
                    permissionConfirmationDialog.setup("Connection Request", "Accept connection request from user " + remoteUID + "?", "yes", "no", this);
                    permissionConfirmationDialog.show();
                    break;
                case Constants.CONSTANT_STRING_ALLOW:
                    replyToConnectionRequest(true);
                    break;
                case Constants.CONSTANT_STRING_DENY:
                    replyToConnectionRequest(false);
                    break;
            }

        } else if (message.startsWith(TelepathyAPI.MESSAGE_DISBAND) && virtualDisplay != null) {
            Telepathy.showShortToast("User " + remoteUID + " has been disconnected.");
            stopEncodingVirtualDisplay();

        } else if (message.startsWith(TelepathyAPI.MESSAGE_INPUT)) {
            switch (allowRemoteControl) {
                case PERMISSION_GRANTED:
                    Gson gson = new Gson();
                    String messagePayload = message.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
                    InputEvent inputEventObject = gson.fromJson(messagePayload, InputEvent.class);
                    decodeInputEvent(inputEventObject);
                    break;
                case PERMISSION_NOT_SET:
                    switch (preferences.getString(Constants.PREFERENCE_REMOTE_CONTROL_RQ, Constants.CONSTANT_STRING_PROMPT)) {
                        case Constants.CONSTANT_STRING_PROMPT:
                            lastPermissionRequest = Constants.PREFERENCE_REMOTE_CONTROL_RQ;
                            permissionConfirmationDialog.setup("Remote Control Request", "Accept remote control request from user " + remoteUID + "?", "yes", "no", this);
                            permissionConfirmationDialog.show();
                            break;
                        case Constants.CONSTANT_STRING_ALLOW:
                            allowRemoteControl = PERMISSION_GRANTED;
                            break;
                        case Constants.CONSTANT_STRING_DENY:
                            allowRemoteControl = PERMISSION_DENIED;
                            break;
                    }
                    break;
            }

        }
    }

    @Override
    public void onBinaryMessage(ByteBufferList byteArray) {
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.view_dialog_base_button_left:
                switch (lastPermissionRequest) {
                    case Constants.PREFERENCE_CONNECTION_RQ:
                        replyToConnectionRequest(true);
                        break;
                    case Constants.PREFERENCE_REMOTE_CONTROL_RQ:
                        allowRemoteControl = PERMISSION_GRANTED;
                        break;
                    case Constants.PREFERENCE_SCREEN_LOCK_UNLOCK:
                        allowScreernlockControl = PERMISSION_GRANTED;
                        decodeInputEvent(lastScreenlockEvent);
                        break;
                }
                break;
            case R.id.view_dialog_base_button_right:
                switch (lastPermissionRequest) {
                    case Constants.PREFERENCE_CONNECTION_RQ:
                        replyToConnectionRequest(false);
                        break;
                    case Constants.PREFERENCE_REMOTE_CONTROL_RQ:
                        allowRemoteControl = PERMISSION_DENIED;
                        break;
                    case Constants.PREFERENCE_SCREEN_LOCK_UNLOCK:
                        allowScreernlockControl = PERMISSION_DENIED;
                        decodeInputEvent(lastScreenlockEvent);
                        break;
                }
                break;
        }
        permissionConfirmationDialog.dismiss();
    }

    private void replyToConnectionRequest(boolean accept) {
        if (accept) {
            ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_BIND_ACCEPTED + remoteUID);
            Telepathy.showShortToast("User " + remoteUID + " has connected.");
            startEncodingVirtualDisplay();
        } else {
            ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_BIND_REJECTED + remoteUID);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.splunk(this);
        permissionConfirmationDialog = new BaseDialog(Telepathy.getContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(this);
            displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            kl = myKM.newKeyguardLock("MyKeyguardLock");
        }

        if (intent != null) {
            if (intent.getAction().equals(ACTION_START)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    activityResult = intent.getIntExtra(EXTRA_ACTIVITY_RESULT, Activity.RESULT_CANCELED);
                    activityResultIntent = intent.getParcelableExtra(EXTRA_ACTIVITY_RESULT_INTENT);
                }

                Telepathy.showShortToast("Remote control service started.");
                ConnectionManager.getInstance().acquireConnection(this, this);
            }

            if (intent.getAction().equals(ACTION_STOP)) {
                disconnect();
            }

            if (intent.getAction().equals(ACTION_DISBAND)) {
                ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_DISBAND);
                Telepathy.showShortToast("User " + remoteUID + " has been disconnected.");
                stopEncodingVirtualDisplay();
            }

            sendBroadcast(new Intent(ACTION_SERVICE_STATE_CHANGED));
        }
        return START_NOT_STICKY;
    }

    public void startEncodingVirtualDisplay() {
        initDisplayParameters();
        createDisplaySurface();

        if (Build.VERSION.SDK_INT >= 21) {
            MediaProjectionManager mediaProjectionManager = ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE));
            mediaProjection = mediaProjectionManager.getMediaProjection(activityResult, activityResultIntent);
            virtualDisplay = mediaProjection.createVirtualDisplay(VIRTUAL_DISPLAY_TAG, streamResolution.x, streamResolution.y, 50,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, encoderInputSurface, null, null);
        } else {
            virtualDisplay = displayManager.createVirtualDisplay(VIRTUAL_DISPLAY_TAG, streamResolution.x, streamResolution.y, 50,
                    encoderInputSurface, DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        }

        encoderThread = new Thread(encoderWorker, "Encoder Thread");
        encoderThread.start();

        updateNotification(true);
    }

    private void createDisplaySurface() {
        try {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE, streamResolution.x, streamResolution.y);

            if (NetworkUtil.getNetworkState(this).equals(Constants.CONSTANT_NETWORK_2G)) {
                bitrateRatio = 0.03125f;
                Telepathy.showLongToast("2G connection detected - reducing video stream quality.");
            } else if (NetworkUtil.getNetworkState(this).equals(Constants.CONSTANT_NETWORK_3G)) {
                bitrateRatio = Float.parseFloat(preferences.getString(Constants.PREFERENCE_BITRATE_MOBILE, ".5"));
            } else {
                bitrateRatio = Float.parseFloat(preferences.getString(Constants.PREFERENCE_BITRATE_WIFI, "1"));
            }

            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) (1024 * 1024 * bitrateRatio));
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 40000);

            Log.i(TAG, "Starting encoder");

            encoder = MediaCodec.createEncoderByType(CodecUtils.MIME_TYPE);
            encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            encoderInputSurface = encoder.createInputSurface();

            encoder.start();
        } catch (IOException e) {
            Logger.log("ENCODER", e.toString(), e);
        }
    }

    private void initDisplayParameters() {
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        float resolutionRatio;
        if (NetworkUtil.getNetworkState(this).equals(Constants.CONSTANT_NETWORK_WFI)) {
            resolutionRatio = Float.parseFloat(preferences.getString(Constants.PREFERENCE_BITRATE_WIFI, "1"));
        } else {
            resolutionRatio = Float.parseFloat(preferences.getString(Constants.PREFERENCE_BITRATE_MOBILE, "0.5"));
        }

        // TODO remove this dirty fix:
        if (resolutionRatio > 1) {
            resolutionRatio = 1;
        }

        display.getRealSize(streamResolution);
        display.getSize(screenResolution);
        Logger.log("RES_S", screenResolution.x + " " + screenResolution.y);
        streamResolution.x = (int) (streamResolution.x * resolutionRatio);
        streamResolution.y = (int) (streamResolution.y * resolutionRatio);
    }

    private void stopEncodingVirtualDisplay() {
        try {
            if (encoder != null) {
                encoder.signalEndOfInputStream();
            }
            updateNotification(false);
        } catch (Exception e) {
            Log.e("SERVICE", e.toString(), e);
        }

        lastPermissionRequest = null;
        lastScreenlockEvent = null;
        allowRemoteControl = PERMISSION_NOT_SET;
        allowScreernlockControl = PERMISSION_NOT_SET;
    }

    private void decodeInputEvent(InputEvent event) {
        if (allowRemoteControl == PERMISSION_GRANTED && !permissionConfirmationDialog.isShowing()) {
            switch (event.getImputType()) {
                case InputEvent.IMPUT_EVENT_TYPE_BACK_BUTTON:
                    try {
                        ShellCommandExecutor.getInstance().runCommand("input keyevent 4");
                    } catch (Exception e) {
                        Logger.log("ENCODER", e.toString(), e);
                    }
                    break;
                case InputEvent.IMPUT_EVENT_TYPE_HOME_BUTTON:
                    try {
                        ShellCommandExecutor.getInstance().runCommand("input keyevent 3");
                    } catch (Exception e) {
                        Logger.log("ENCODER", e.toString(), e);
                    }
                    break;
                case InputEvent.IMPUT_EVENT_TYPE_RECENT_BUTTON:
                    try {
                        ShellCommandExecutor.getInstance().runCommand("input keyevent 187");
                    } catch (Exception e) {
                        Logger.log("ENCODER", e.toString(), e);
                    }
                    break;
                case InputEvent.IMPUT_EVENT_TYPE_LOCK_UNLOCK_BUTTON:
                    switch (allowScreernlockControl) {
                        case PERMISSION_GRANTED:
                            if (myKM.inKeyguardRestrictedInputMode()) {
                                kl.disableKeyguard();
                                myKM.exitKeyguardSecurely(null);
                                try {
                                    ShellCommandExecutor.getInstance().runCommand("input keyevent 26");
                                } catch (Exception e) {
                                    Logger.log("ENCODER", e.toString(), e);
                                }
                            } else {
                                kl.reenableKeyguard();
                                try {
                                    ShellCommandExecutor.getInstance().runCommand("input keyevent 26");
                                } catch (Exception e) {
                                    Logger.log("ENCODER", e.toString(), e);
                                }
                            }
                            break;
                        case PERMISSION_NOT_SET:
                            switch (preferences.getString(Constants.PREFERENCE_SCREEN_LOCK_UNLOCK, Constants.CONSTANT_STRING_PROMPT)) {
                                case Constants.CONSTANT_STRING_PROMPT:
                                    lastPermissionRequest = Constants.PREFERENCE_SCREEN_LOCK_UNLOCK;
                                    lastScreenlockEvent = event;
                                    permissionConfirmationDialog.setup("Screen Lock Control Request", "Allow user " + remoteUID + " to lock/unlock your device?", "yes", "no", this);
                                    permissionConfirmationDialog.show();
                                    break;
                                case Constants.CONSTANT_STRING_ALLOW:
                                    allowScreernlockControl = PERMISSION_GRANTED;
                                    break;
                                case Constants.CONSTANT_STRING_DENY:
                                    allowScreernlockControl = PERMISSION_DENIED;
                                    break;
                            }
                            break;
                        case InputEvent.IMPUT_EVENT_TYPE_TOUCH:
                            try {
                                float x = event.getTouchEventX() * screenResolution.x;
                                float y = event.getTouchEventY() * screenResolution.y;
                                ShellCommandExecutor.getInstance().runCommand("input tap " + x + " " + y);
                            } catch (Exception e) {
                                Logger.log("ENCODER", e.toString(), e);
                            }
                            break;
                        case InputEvent.IMPUT_EVENT_TYPE_LONG_PRESS:
                            try {
                                float x = event.getTouchEventX() * screenResolution.x;
                                float y = event.getTouchEventY() * screenResolution.y;
                                ShellCommandExecutor.getInstance().runCommand("input swipe " + x + " " + y + " " + x + " " + y + " " + InputEvent.IMPUT_EVENT_LONG_PRESS_DURATION);
                            } catch (Exception e) {
                                Logger.log("ENCODER", e.toString(), e);
                            }
                            break;
                        case InputEvent.IMPUT_EVENT_TYPE_SWIPE:
                            try {
                                float x = event.getTouchEventX() * screenResolution.x;
                                float y = event.getTouchEventY() * screenResolution.y;
                                float x1 = event.getToucEventX1() * screenResolution.x;
                                float y1 = event.getTouchEventY1() * screenResolution.y;
                                ShellCommandExecutor.getInstance().runCommand("input swipe " + x + " " + y + " " + x1 + " " + y1 + " " + InputEvent.IMPUT_EVENT_FLING_DURATION);
                            } catch (Exception e) {
                                Logger.log("ENCODER", e.toString(), e);
                            }
                            break;
                    }
            }
        }
    }

    @Override
    public void onDestroy() {
        disconnect();
        sendBroadcast(new Intent(ACTION_SERVICE_STATE_CHANGED));
        Telepathy.showShortToast("Remote control service stopped.");
        super.onDestroy();
    }

    private void disconnect() {
        stopEncodingVirtualDisplay();
        ConnectionManager.getInstance().releaseConnection(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Runnable encoderWorker = new Runnable() {

        @Override
        public void run() {

            boolean encoderDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (!encoderDone) {
                try {
                    int encoderStatus = encoder.dequeueOutputBuffer(info, CodecUtils.TIMEOUT_USEC);

                    if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // TODO
                    } else if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // TODO Force redraw the screen so the video output framerate is constant?
                    } else if (encoderStatus != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                        ByteBuffer encodedData = encoder.getOutputBuffers()[encoderStatus];

                        String metadata = "";
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            metadata = TelepathyAPI.MESSAGE_VIDEO_METADATA + info.offset + "," + info.size + "," +
                                    info.presentationTimeUs + "," + info.flags + "," + streamResolution.x + "," + streamResolution.y + ";";
                        } else {
                            metadata = TelepathyAPI.MESSAGE_VIDEO_METADATA + info.offset + "," + info.size + "," +
                                    info.presentationTimeUs + "," + info.flags + ";";
                        }

                        byte[] meta = new byte[CodecUtils.VIDEO_META_MAX_LEN];
                        System.arraycopy(metadata.getBytes(), 0, meta, 0, metadata.getBytes().length);

                        try {
                            byte[] video = new byte[info.size];
                            encodedData.get(video, info.offset, info.offset + info.size);
                            ConnectionManager.getInstance().sendBinaryMessage(Utils.mergeByteArrays(meta, video));
                        } catch (BufferUnderflowException e) {
                            Logger.log("ENCODER", e.toString(), e);
                            continue;
                        }

                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }

                    encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    if (encoderDone) {
                        Logger.log("ENCODER", "END OF STREAM");
                        if (Build.VERSION.SDK_INT >= 21) {
                            mediaProjection.stop();
                        }
                        virtualDisplay.release();
                        virtualDisplay = null;
                        encoderInputSurface.release();
                        encoderInputSurface = null;
                        encoder.stop();
                        encoder.release();
                        encoder = null;
                    }
                } catch (Exception e) {
                    Logger.log("ENCODER", e.toString(), e);
                }
            }
        }
    };

    private void updateNotification(boolean show) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (show) {
            Intent intent = new Intent(this, RemoteControlService.class);
            intent.setAction(ACTION_DISBAND);
            PendingIntent stopServiceIntent = PendingIntent.getService(this, 0, intent, 0);
            Notification.Builder mBuilder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .addAction(R.drawable.ic_media_stop, "STOP SHARING SCREEN", stopServiceIntent)
                    .setContentTitle("User " + remoteUID + " connected.")
                    .setTicker("Screen sharing in progress...");
            notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        } else {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }
}
