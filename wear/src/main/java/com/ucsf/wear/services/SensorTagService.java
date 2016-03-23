package com.ucsf.wear.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.estimote.sdk.Beacon;
import com.ucsf.core.data.DataManager;
import com.ucsf.core.data.Entry;
import com.ucsf.core.data.RSSI;
import com.ucsf.core.data.SharedTables;
import com.ucsf.core.data.Timestamp;
import com.ucsf.core.services.Annotations;
import com.ucsf.core.services.BackgroundService;
import com.ucsf.core.services.BeaconMonitoring;
import com.ucsf.core.services.ServiceId;
import com.ucsf.core.services.ServiceParameter;
import com.ucsf.wear.R;
import com.ucsf.wear.data.Settings;

import java.util.List;

/**
 * Service responsible of saving beacons RSSI.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public class SensorTagService extends BackgroundService implements SensorTagMonitoring.SensorTagListener {
    private static final String   TAG                         = "ucsf:SensorTagService";
    private static final String   KEY_UPDATE_RANGING_INTERVAL = "a";
    private static       Provider mInstance;
    private static       SensorTagMonitoring mSensorTagMonitor;

    @Override
    public Provider getProvider() {
        return getProvider(this);
    }

    /** Returns the service provider. */
    public static Provider getProvider(Context context) {
        if (mInstance == null)
            mInstance = new Provider(context);
        return mInstance;
    }

    @Override
    public void onSensorTagReading(List<String> readings) {
        // Get beacons measured power

        // Put the sensor readings into the database
        try (DataManager instance = DataManager.get(this)) {
            SharedTables.Estimote.getTable(instance).add(
                    new Entry(DataManager.KEY_PATIENT_ID    , Settings.getCurrentUserId(this)),
                    new Entry(DataManager.KEY_TIMESTAMP     , Timestamp.getTimestamp()),
                    new Entry(SharedTables.Estimote.KEY_RSSI, "", true)
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to save beacons RSSI: ", e);
        }
    }

    @Override
    protected void onStart() throws Exception {
        Log.d(TAG, "onStart()");
        Provider provider = getProvider();
        mSensorTagMonitor = new SensorTagMonitoring();
        mSensorTagMonitor.addRangingListener(this);
        mSensorTagMonitor.startMonitoring(this);

        //display foreground notification
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext());

        Intent intent = new Intent(this.getApplicationContext(), SensorTagMonitoring.class);
        PendingIntent pendingIntent
                = PendingIntent.getActivity(this.getApplicationContext(), 0, intent, 0);

        Notification notification = builder
                .setSmallIcon(com.ucsf.core.R.drawable.icon)
                .setContentTitle("SensorTagTI Data Capture")
                .setContentText("Automatic Mode Enabled")
                .setContentInfo("ContentInfo")
                .setAutoCancel(true)
                .build();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        prefs.edit().putBoolean("automaticModeEnabled", true).commit();

        startForeground(54330216, notification);

    }

    @Override
    protected void onStop() {
        mSensorTagMonitor.stopMonitoring();
        mSensorTagMonitor.removeRangingListener(this);

        //remove foreground notification
        stopForeground(true);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        prefs.edit().putBoolean("automaticModeEnabled", false).commit();

    }

    /**
     * Ranging service provider class.
     */
    public static class Provider extends BackgroundService.Provider {
        private final ServiceParameter<Long> mIndoorWaitPeriod;
        private final ServiceParameter<Long> mOutdoorWaitPeriod;
        private final ServiceParameter<Long> mScanPeriod;

        private Provider(Context context) {
            super(context, SensorTagService.class, ServiceId.PW_SensorTagService);

            mIndoorWaitPeriod = addParameter("INDOOR_WAIT_PERIOD",
                    R.string.parameter_indoor_wait_period, 60000L);
            mOutdoorWaitPeriod = addParameter("OUTDOOR_WAIT_PERIOD",
                    R.string.parameter_outdoor_wait_period, 300000L);
            mScanPeriod = addParameter("SCAN_PERIOD",
                    R.string.parameter_scan_period, 6000L);
        }

        /**
         * Changes the ranging interval depending on if the patient is at home or not.
         */
        @Annotations.MappedMethod(KEY_UPDATE_RANGING_INTERVAL)
        public void setIndoorMode(boolean enable) {
            /*
            if (enable) {
                SensorTagMonitoring.resetMonitoringPeriods(mIndoorWaitPeriod.get(),
                        mScanPeriod.get());
            } else {
                SensorTagMonitoring.resetMonitoringPeriods(mOutdoorWaitPeriod.get(),
                        mScanPeriod.get());
            }*/
        }

        /**
         * Returns the period between two consecutive beacons scans.
         */
        public long getWaitPeriod() {
            return mIndoorWaitPeriod.get();
        }

        /**
         * Returns the period during which beacons scanning is done.
         */
        public long getScanPeriod() {
            return mScanPeriod.get();
        }
    }
}
