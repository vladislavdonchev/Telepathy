package net.hardcodes.telepathy.activities;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.koushikdutta.async.ByteBufferList;

import net.hardcodes.telepathy.R;
import net.hardcodes.telepathy.tools.ConnectionManager;
import net.hardcodes.telepathy.tools.Utils;

public class ScreenCaptureRequestActivity extends Activity implements ConnectionManager.WebSocketConnectionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_capture_request);

        if (Build.VERSION.SDK_INT >= 21) {
            MediaProjectionManager mediaProjectionManager = ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE));
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 3000);
        }

        ConnectionManager.getInstance().acquireConnection(this, this);
    }

    @Override
    protected void onDestroy() {
        ConnectionManager.getInstance().releaseConnection(this);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 3000) {
            Utils.startService(resultCode, data);
            finish();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConnectionAcquired() {
    }

    @Override
    public void onError(int errorCode) {
    }

    @Override
    public void onTextMessage(String message) {
    }

    @Override
    public void onBinaryMessage(ByteBufferList byteBufferList) {
    }
}
