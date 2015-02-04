package net.hardcodes.telepathy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends Activity {
    private static final int MY_REQUEST_CODE = 9999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void startClient(View v) {
        new AddressInputDialog().show(getFragmentManager(), "Remote Control");
    }

    public void startServer(View v) {
        Intent startServerIntent = new Intent(MainActivity.this, RemoteControlService.class);
        startServerIntent.setAction("START");
        startService(startServerIntent);
        finish();
    }
}
