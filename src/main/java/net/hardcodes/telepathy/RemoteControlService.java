package net.hardcodes.telepathy;

import android.app.KeyguardManager;
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
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.google.gson.Gson;
import com.koushikdutta.async.ByteBufferList;

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

public class RemoteControlService extends Service implements ConnectionManager.WebSocketConnectionListener {

    public static final String ACTION_SERVICE_STATE_CHANGED = "stateChanged";

    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";

    private static final String VIRTUAL_DISPLAY_TAG = "ScreenRecorder";
    private static final String TAG = "StreamingServer";
    private KeyguardManager myKM;
    private KeyguardManager.KeyguardLock kl;
    private SharedPreferences preferences;
    private DisplayManager displayManager;
    private Surface encoderInputSurface = null;
    private VirtualDisplay virtualDisplay = null;
    private MediaCodec encoder = null;
    private Thread encoderThread = null;

    private float bitrateRatio;
    private int deviceWidth;
    private int deviceHeight;
    private Point resolution = new Point();

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
            String remoteUID = message.split(TelepathyAPI.MESSAGE_UID_DELIMITER)[1];
            ConnectionManager.getInstance().sendTextMessage(TelepathyAPI.MESSAGE_BIND_ACCEPTED + remoteUID);
            Telepathy.showShortToast("User " + remoteUID + " has connected.");
            startEncodingVirtualDisplay();

        } else if (message.startsWith(TelepathyAPI.MESSAGE_DISBAND)) {
            stopEncodingVirtualDisplay();

        } else if (message.startsWith(TelepathyAPI.MESSAGE_INPUT)) {
            Gson gson = new Gson();
            String messagePayload = message.split(TelepathyAPI.MESSAGE_PAYLOAD_DELIMITER)[1];
            InputEvent inputEventObject = gson.fromJson(messagePayload, InputEvent.class);
            decodeInputEvent(inputEventObject);

        }
    }

    @Override
    public void onBinaryMessage(ByteBufferList byteArray) {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.splunk(this);
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
                Telepathy.showShortToast("Remote control service started.");
                ConnectionManager.getInstance().acquireConnection(this, this);
            }

            if (intent.getAction().equals(ACTION_STOP)) {
                disconnect();
            }

            sendBroadcast(new Intent(ACTION_SERVICE_STATE_CHANGED));
        }
        return START_NOT_STICKY;
    }

    public void startEncodingVirtualDisplay() {
        initDisplayParameters();

        try {
            encoderInputSurface = createDisplaySurface();
        } catch (IOException e) {
            Logger.log("ENCODER", e.toString(), e);
        }
        virtualDisplay = displayManager.createVirtualDisplay(VIRTUAL_DISPLAY_TAG, resolution.x, resolution.y, 50,
                encoderInputSurface, DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                    | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);

        encoderThread = new Thread(new EncoderWorker(), "Encoder Thread");
        encoderThread.setPriority(Thread.MAX_PRIORITY);
        encoderThread.start();
    }

    private Surface createDisplaySurface() throws IOException {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(CodecUtils.MIME_TYPE, resolution.x, resolution.y);

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

        Surface surface = encoder.createInputSurface();

        encoder.start();
        return surface;
    }

    private void initDisplayParameters() {
        DisplayMetrics dm = new DisplayMetrics();
        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        display.getMetrics(dm);
        deviceWidth = dm.widthPixels;
        deviceHeight = dm.heightPixels;
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

        display.getRealSize(resolution);
        resolution.x = (int) (resolution.x * resolutionRatio);
        resolution.y = (int) (resolution.y * resolutionRatio);
    }

    private void stopEncodingVirtualDisplay() {
        try {
            if (encoder != null) {
                encoder.signalEndOfInputStream();
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

    private void decodeInputEvent(InputEvent event) {
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
            case InputEvent.IMPUT_EVENT_TYPE_TOUCH:
                try {
                    float x = event.getToucEventX() * deviceWidth;
                    float y = event.getTouchEventY() * deviceHeight;
                    ShellCommandExecutor.getInstance().runCommand("input tap " + x + " " + y);
                } catch (Exception e) {
                    Logger.log("ENCODER", e.toString(), e);
                }
                break;
            case InputEvent.IMPUT_EVENT_TYPE_LONG_PRESS:
                try {
                    float x = event.getToucEventX() * deviceWidth;
                    float y = event.getTouchEventY() * deviceHeight;
                    ShellCommandExecutor.getInstance().runCommand("input swipe " + x + " " + y + " " + x + " " + y + " " + InputEvent.IMPUT_EVENT_LONG_PRESS_DURATION);
                } catch (Exception e) {
                    Logger.log("ENCODER", e.toString(), e);
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
                    Logger.log("ENCODER", e.toString(), e);
                }
                break;
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
        ConnectionManager.getInstance().releaseConnection(this);
        stopEncodingVirtualDisplay();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class EncoderWorker implements Runnable {

        @Override
        public void run() {

            boolean encoderDone = false;
            while (encoder != null && !encoderDone) {
                try {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
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
                                    info.presentationTimeUs + "," + info.flags + "," + resolution.x + "," + resolution.y + ";";
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
                        encoder.stop();
                        encoder.release();
                        encoder = null;
                        break;
                    }
                } catch (Exception e) {
                    Logger.log("ENCODER", e.toString(), e);
                }
            }
        }
    }
}
