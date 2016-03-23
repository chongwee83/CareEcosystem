package com.ucsf.wear.services;

import android.app.AlarmManager;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.ucsf.core.services.Annotations;
import com.ucsf.core.services.ResponseListener;
import com.ucsf.core.services.ServiceId;
import com.ucsf.core.services.UploaderService;
import com.ucsf.wear.R;

/**
 * Service responsible of pushing data to the phone once a day.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public class PhoneUploaderService extends UploaderService {
    private static final String TAG               = "ucsf:PhoneUploader";
    private static final String KEY_PUSH_DATA     = "a";
    private static final String KEY_SYNC_UP       = "b";
    private static final String KEY_PUSH_ALL_DATA = "c";

    private static Provider mInstance;

    /**
     * Returns the service provider.
     */
    public static Provider getProvider(Context context) {
        if (mInstance == null)
            mInstance = new Provider(context);
        return mInstance;
    }

    @Override
    public Provider getProvider() {
        return getProvider(getContext());
    }

    /**
     * Phone uploader service provider class.
     */
    public static class Provider extends UploaderService.Provider {
        private Provider(Context context) {
            super(context, PhoneUploaderService.class, ServiceId.PW_PhoneUploaderService,
                    AlarmManager.INTERVAL_HOUR);

            addCallback("PUSH_DATA"    , R.string.parameter_push_data, KEY_PUSH_DATA);
            addCallback("PUSH_ALL_DATA", R.string.parameter_push_all_data, KEY_PUSH_ALL_DATA);
            addCallback("SYNC_UP"      , R.string.parameter_sync     , KEY_SYNC_UP);
        }

        @Override
        public void commit() {
            try {
                onStartCommit();
                StartupService.loadTables(context);
                DeviceInterface.sendData(context, getMonitoredTables());
                onFinishCommit(true);
            } catch (Exception e) {
                Log.e(TAG, "Failed to push data to the phone: ", e);
                onFinishCommit(false);
            }
        }

        @Override
        public void startService(final ResponseListener listener) {
            super.startService(new ResponseListener() {
                @Override
                public void onSuccess() {
                    // Check if we need to push the data immediately
                    if (System.currentTimeMillis() - getLastCommit() > getInterval())
                        commit();
                    listener.onSuccess();
                }

                @Override
                public void onFailure(String error, Throwable e) {
                    listener.onFailure(error, e);
                }
            });
        }

        /**
         * Pushes the watch tables to the phone.
         */
        @Annotations.MappedMethod(KEY_PUSH_DATA)
        public void pushData() {
            Toast.makeText(context, R.string.toast_pushing_data, Toast.LENGTH_SHORT).show();
            commit();
        }

        /**
         * Pushes the watch tables to the phone, including already committed data.
         */
        @Annotations.MappedMethod(KEY_PUSH_ALL_DATA)
        public void pushAllData() {
            Toast.makeText(context, R.string.toast_pushing_data, Toast.LENGTH_SHORT).show();
            try {
                onStartCommit();
                StartupService.loadTables(context);
                DeviceInterface.sendData(context, getMonitoredTables(), true);
                onFinishCommit(true);
            } catch (Exception e) {
                Log.e(TAG, "Failed to push data to the phone: ", e);
                onFinishCommit(false);
            }
        }

        /**
         * Asks the phone to send patient information to the watch.
         */
        @Annotations.MappedMethod(KEY_SYNC_UP)
        public void syncUp() {
            Toast.makeText(context, R.string.toast_sync, Toast.LENGTH_SHORT).show();
            DeviceInterface.requestPatientInfo(context);
        }
    }

}
