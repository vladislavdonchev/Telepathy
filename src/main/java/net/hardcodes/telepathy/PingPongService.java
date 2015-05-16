package net.hardcodes.telepathy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import net.hardcodes.telepathy.model.TelepathyAPI;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PingPongService extends Service {

    public final static String ACTION_START = "start";
    public final static String ACTION_STOP = "stop";

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture pingPongHandle;
    private Runnable pingPongRunnable;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getAction().equals(ACTION_START)) {
            Logger.log("WS", "START PING");
            pingPongRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        ConnectionManager.getInstance().ping(TelepathyAPI.MESSAGE_HEARTBEAT);
                        Logger.log("WS", "PING");
                    } catch (Exception e) {
                        Logger.log("WS", e.toString(), e);
                    }
                }
            };
            pingPongHandle = scheduler.scheduleAtFixedRate(pingPongRunnable, 0, 10, TimeUnit.SECONDS);
        }

        if (intent.getAction().equals(ACTION_STOP)) {
            Logger.log("WS", "STOP PING");
            pingPongHandle.cancel(true);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
