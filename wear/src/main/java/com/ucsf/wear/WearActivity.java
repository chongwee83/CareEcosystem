package com.ucsf.wear;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;

import com.ucsf.core.services.BeaconMonitoring;
import com.ucsf.wear.services.RangingService;
import com.ucsf.wear.services.SensorTagService;
import com.ucsf.wear.services.StartupService;

/**
 * Main watch activity. Only display a nice picture. Starts also the services if needed.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public class WearActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StartupService.startServices(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear);

        // Initialize the view
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
            }
        });

        if (!BeaconMonitoring.isBluetoothEnabled())
            BeaconMonitoring.enableBluetooth(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BeaconMonitoring.REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                RangingService.Provider provider = RangingService.getProvider(this);
                if (!provider.isServiceRunning() && provider.isServiceEnabled())
                    provider.start();

                SensorTagService.Provider sensorTagProvider = SensorTagService.getProvider(this);
                if (!sensorTagProvider.isServiceRunning() && sensorTagProvider.isServiceEnabled())
                    sensorTagProvider.start();

            } else {
                BeaconMonitoring.enableBluetooth(this);
            }
        } else
            super.onActivityResult(requestCode, resultCode, data);
    }

}
