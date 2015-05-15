package net.hardcodes.telepathy;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Logger;

public class PingPongService extends Service {

    public final static String ACTION_START = "start";
    public final static String ACTION_STOP = "stop";

    //private HandlerThread pingPongThread;
    private Handler pingPongHandler;
    private Runnable pingPongRunnable;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getAction().equals(ACTION_START)) {
            Logger.log("WS", "START PING");
            //pingPongThread = new HandlerThread("PingPongThread");
            //pingPongThread.start();
            //pingPongHandler = new Handler(pingPongThread.getLooper());
            pingPongHandler = new Handler();
            pingPongRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        ConnectionManager.getInstance().ping(TelepathyAPI.MESSAGE_HEARTBEAT);
                        Logger.log("WS", "PING");
                        pingPongHandler.postDelayed(this, 10 * 1000);
                        Logger.log("WS", "POSTING NEXT PING");
                    } catch (Exception e) {
                        Logger.log("WS", e.toString(), e);
                    }
                }
            };
            pingPongHandler.post(pingPongRunnable);
        }

        if (intent.getAction().equals(ACTION_STOP)) {
            Logger.log("WS", "STOP PING");
            pingPongHandler.removeCallbacks(pingPongRunnable);
            pingPongHandler = null;
            //pingPongThread.quitSafely();
            //pingPongThread = null;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
