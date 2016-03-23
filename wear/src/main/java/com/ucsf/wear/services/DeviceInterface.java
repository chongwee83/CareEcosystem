package com.ucsf.wear.services;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.ucsf.core.data.DataManager;
import com.ucsf.core.data.Entry;
import com.ucsf.core.data.SharedTables;
import com.ucsf.core.data.Timestamp;
import com.ucsf.core.services.Messages;
import com.ucsf.core.services.Messages.Event;
import com.ucsf.core.services.Messages.Request;
import com.ucsf.core.services.ServiceCallback;
import com.ucsf.core.services.ServiceDescriptor;
import com.ucsf.core.services.ServiceId;
import com.ucsf.core.services.ServiceParameter;
import com.ucsf.core.services.Services;
import com.ucsf.wear.data.Settings;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Service responsible of the communication between the phone and the watch.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public class DeviceInterface extends com.ucsf.core.services.DeviceInterface {
    private static final String TAG = "ucsf:DeviceInterface";

    /**
     * Sets the patient information from the received data.
     */
    private static void setPatientInfo(final Context context, JSONObject data) throws Exception {
        final String patientId = data.getString(DataManager.KEY_PATIENT_ID);
        if (patientId == null || patientId.isEmpty() ||
                patientId.equals(Settings.getCurrentUserId(context)))
            return;

        // Restart the services on the main thread
        Handler mainHandler = new Handler(context.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Services.stopServices();
                    Settings.setCurrentUserId(context, patientId);
                    StartupService.startServices(context);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start services: ", e);
                }
            }
        });
    }

    /**
     * Sends a request in order to get patient information.
     */
    public static void requestPatientInfo(final Context context) {
        sendRequest(context, Request.PATIENT_INFO, new RequestListener() {
            @Override
            public void requestProcessed(Request request, JSONObject data) throws Exception {
                setPatientInfo(context, data);
            }
        });
    }

    /**
     * Sends the uncommitted content of the given tables using Android Wear data layer API.
     * http://developer.android.com/training/wearables/data-layer/data-items.html
     * If the handset and wearable devices are disconnected, the data is
     * buffered and synced when the connection is re-established.
     * this is why the dataAPI is used over the messageAPI
     */
    public static void sendData(final Context context, final Collection<DataManager.Table> tables) {
        sendData(context, tables, false);
    }

    /**
     * Sends the uncommitted content of the given tables using Android Wear data layer API.
     * http://developer.android.com/training/wearables/data-layer/data-items.html
     * If the handset and wearable devices are disconnected, the data is
     * buffered and synced when the connection is re-established.
     * this is why the dataAPI is used over the messageAPI
     */
    public static void sendData(final Context context, final Collection<DataManager.Table> tables,
                                final boolean includingCommittedData)
    {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try (Connection connection = openConnection(context)) {
                    DataManager.Condition notCommitted =
                            new DataManager.Condition.Equal<>(DataManager.KEY_IS_COMMITTED, 0);
                    DataManager.Condition timestampCond =
                            new DataManager.Condition.LessEqual<>(DataManager.KEY_TIMESTAMP,
                                    Timestamp.getTimestamp());
                    DataManager.Condition endCond =
                            new DataManager.Condition.LessEqual<>(SharedTables.GroundTrust.KEY_END,
                                    Timestamp.getTimestamp());
                    DataResultCallback callback = new DataResultCallback(context);

                    try (DataManager instance = DataManager.get(context)) {
                        Log.d(TAG, "Sending tables content...");
                        for (DataManager.Table table : tables) {
                            DataManager.Cursor cursor;
                            if (includingCommittedData) {
                                if (table == SharedTables.GroundTrust.getTable(instance))
                                    cursor = table.fetch(endCond);
                                else
                                    cursor = table.fetch(timestampCond);
                            } else {
                                if (table == SharedTables.GroundTrust.getTable(instance))
                                    cursor = table.fetch(notCommitted, endCond);
                                else
                                    cursor = table.fetch(notCommitted, timestampCond);
                            }

                            if (cursor != null && cursor.moveToFirst()) {
                                do {

                                    //Log.d(TAG, String.format("number of values pushed at timestamp %s:\n%s", Timestamp.getTimestamp(), cursor.getCount()));
                                    PutDataMapRequest dataMapRequest = PutDataMapRequest.create(
                                            createTableEntryPath(table, cursor));

                                    DataMap map = dataMapRequest.getDataMap();
                                    for (final DataManager.TableField field : table.fields) {
                                        switch (field.type) {
                                            case Text:
                                            case UniqueText:
                                                map.putString(field.tag, cursor.getString(field.tag));
                                                break;
                                            case Integer:
                                            case Boolean:
                                                if (!field.tag.equals(DataManager.KEY_IS_COMMITTED))
                                                    map.putInt(field.tag, cursor.getInt(field.tag));
                                                break;
                                            case Real:
                                                map.putDouble(field.tag, cursor.getDouble(field.tag));
                                                break;
                                            case Long:
                                                map.putLong(field.tag, cursor.getLong(field.tag));
                                                break;
                                            case Blob:
                                                map.putByteArray(field.tag, cursor.getBlob(field.tag));
                                                break;
                                        }
                                    }

                                    sendData(connection, dataMapRequest, callback);
                                } while (cursor.moveToNext());
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send tables content: ", e);
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StartupService.startServices(this); // Make sure that all the tables are registered
    }

    @Override
    protected void onEventReceived(Event event, JSONObject data) throws Exception {
        switch (event) {
            case PROFILE_CHANGED:
                setPatientInfo(this, data);
                break;
            case TOGGLE_SERVICES:
                toggleServices(data.getBoolean(Messages.VALUE));
                break;
            case PATIENT_OUTSIDE:
                updateRangingWaitInterval(false);
                break;
            case PATIENT_INSIDE:
                updateRangingWaitInterval(true);
                break;
            default:
                Log.w(TAG, String.format("Unexpected event '%s'!", event.getTag()));
                break;
        }
    }

    @Override
    protected void onRequestReceived(Request request, JSONObject data) throws Exception {
        switch (request) {
            case PATIENT_INFO:
                replyToRequest(this, request);
                setPatientInfo(this, data);
                break;
            case PARAMETER_UPDATE:
                replyToRequest(this, request);
                updateParameter(ServiceId.valueOf(data.getString(Messages.SERVICE_ID)),
                        data.getString(Messages.PARAMETER_ID), data);
                break;
            case PARAMETER_RESET:
                replyToRequest(this, request);
                resetParameter(ServiceId.valueOf(data.getString(Messages.SERVICE_ID)),
                        data.getString(Messages.PARAMETER_ID));
                break;
            case CALLBACK_EXEC:
                replyToRequest(this, request);
                execParameter(ServiceId.valueOf(data.getString(Messages.SERVICE_ID)),
                        data.getString(Messages.PARAMETER_ID));
                break;
            case SERVICES:
                sendBackWatchServices();
                break;
            case SERVICES_STATUS:
                sendBackServiceStatus(ServiceId.valueOf(data.getString(Messages.SERVICE_ID)));
                break;
            case PING:
                replyToRequest(this, request);
                requestPatientInfo(this);
                break;
            default:
                Log.w(TAG, String.format("Unexpected request '%s'!", request.getTag()));
                break;
        }
    }

    /**
     *
     */
    private void updateRangingWaitInterval(boolean atHome) {
        RangingService.getProvider(this).setIndoorMode(atHome);
    }

    /**
     * Sends the list of registered watch services.
     */
    private void sendBackWatchServices() throws Exception {
        List<ServiceDescriptor> services = new ArrayList<>();
        for (Services.Provider provider : StartupService.getApplicationProviders(this))
            services.add(new ServiceDescriptor(provider));
        replyToRequest(this, Request.SERVICES, ServiceDescriptor.saveDescriptors(services));
    }

    /**
     * Sends the given service status.
     */
    private void sendBackServiceStatus(ServiceId service) throws Exception {
        Services.Provider provider = Services.getProvider(service);
        replyToRequest(this, Request.SERVICES_STATUS,
                new Entry(Messages.SERVICE_ID, service.toString()),
                new Entry(Messages.VALUE, ServiceDescriptor.getStatus(provider).toString()));
    }

    /**
     * Updates the given service parameter.
     */
    private void updateParameter(ServiceId service, String parameterTag, JSONObject data)
            throws Exception {
        Serializable value = (Serializable) data.get(Messages.VALUE);
        Services.Provider provider = Services.getProvider(service);
        ServiceParameter parameter = provider.getParameter(parameterTag);

        Log.d(TAG, String.format("Update parameter '%s:%s' from %s to %s.",
                provider.getServiceName(),
                parameterTag,
                parameter.get().toString(),
                value.toString()));

        boolean isRunning = provider.isServiceRunning();
        boolean isEnabled = provider.isServiceEnabled();
        provider.stop();
        parameter.set(value);
        if (isRunning || (!isEnabled && provider.isServiceEnabled()))
            provider.start();
    }

    /**
     * Resets the given service parameter.
     */
    private void resetParameter(ServiceId service, String parameterTag) throws Exception {
        Services.Provider provider = Services.getProvider(service);
        ServiceParameter parameter = provider.getParameter(parameterTag);

        Log.d(TAG, String.format("Reset parameter '%s:%s'.",
                provider.getServiceName(),
                parameterTag));

        boolean isRunning = provider.isServiceRunning();
        boolean isEnabled = provider.isServiceEnabled();
        provider.stop();
        parameter.reset();
        if (isRunning || (!isEnabled && provider.isServiceEnabled()))
            provider.start();
    }

    /**
     * Executes the given service callback.
     */
    private void execParameter(ServiceId service, String callbackTag) throws Exception {
        Services.Provider provider = Services.getProvider(service);
        ServiceCallback callback = provider.getCallback(callbackTag);
        callback.execute();
    }

    /**
     * Toggles the application services.
     */
    private void toggleServices(boolean enable) {
        Services.enableServices(this, enable);
    }

    private static abstract class RequestListener
            implements com.ucsf.core.services.DeviceInterface.RequestListener {
        @Override
        public void requestTimeout(Request request) throws Exception {
            Log.w(TAG, String.format("Request '%s' timed out.", request.getTag()));
        }

        @Override
        public void requestCancelled(Request request) throws Exception {
            Log.d(TAG, String.format("Request '%s' cancelled.", request.getTag()));
        }
    }

    private static class DataResultCallback implements ResultCallback<DataApi.DataItemResult> {
        private final Context mContext;

        public DataResultCallback(Context context) {
            mContext = context;
        }

        @Override
        public synchronized void onResult(DataApi.DataItemResult dataItemResult) {
            Status status = dataItemResult.getStatus();
            if (status.isSuccess()) {
                try (DataManager instance = DataManager.get(mContext)) {
                    String                path  = dataItemResult.getDataItem().getUri().getPath();
                    Pair<String, Integer> p     = parseMessagePath(path);
                    String                table = p.first;
                    int                   rowId = p.second;

                    // Mark the row as committed
                    instance.update(table,
                            new Entry[]{
                                    new Entry(DataManager.KEY_IS_COMMITTED, 1)
                            },
                            new DataManager.Condition.Equal<>(DataManager.KEY_ROW_ID, rowId)
                    );
                } catch (Exception e) {
                    Log.e(TAG, "An error occurred while reading result: ", e);
                }
            } else
                Log.e(TAG, "Failed to push data item, status code: " + status.getStatusCode());
        }
    }

}
