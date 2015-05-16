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

    public final static String ACTION_START = "startPing";
    public final static String ACTION_STOP = "stopPing";

    private ScheduledExecutorService scheduler;
    private ScheduledFuture pingPongHandle;
    private Runnable pingPongRunnable;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (ACTION_START.equals(intent.getAction())) {
            startPing();
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            stopPing();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void startPing() {
        if (scheduler == null) {
            Logger.log("WS", "START PING");
            scheduler = Executors.newScheduledThreadPool(1);
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
    }

    private void stopPing() {
        if (scheduler != null) {
            Logger.log("WS", "STOP PING");
            pingPongHandle.cancel(true);
            scheduler.shutdownNow();

            scheduler = null;
            pingPongHandle = null;
            pingPongRunnable = null;

            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        stopPing();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
