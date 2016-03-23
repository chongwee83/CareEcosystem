package com.ucsf.services;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.ucsf.core.data.DataManager;
import com.ucsf.core.data.DeviceLocation;
import com.ucsf.core.data.Entry;
import com.ucsf.core.data.JSON;
import com.ucsf.core.data.Sender;
import com.ucsf.core.services.Messages;
import com.ucsf.core.services.Messages.Event;
import com.ucsf.core.services.Messages.Request;
import com.ucsf.core.services.ServiceId;
import com.ucsf.core.services.UserMonitoringService;
import com.ucsf.data.Settings;

import org.json.JSONObject;

import java.io.Serializable;
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
     * Sends the patient information to the watch.
     */
    public static void sendPatientInfo(Context context) {
        sendEvent(context, Event.PROFILE_CHANGED, getPatientInfo(context));
    }

    /**
     * Toggles watch services
     */
    public static void toggleWatchServices(Context context, boolean enable) {
        sendEvent(context, Event.TOGGLE_SERVICES, enable);
    }

    /**
     * Pings the watch.
     */
    public static void pingWatch(Context context,
                                 com.ucsf.core.services.DeviceInterface.RequestListener listener)
    {
        sendRequest(context, Request.PING, listener);
    }

    /**
     * Requests watch services.
     */
    public static void requestWatchServices(Context context,
                                            com.ucsf.core.services.DeviceInterface.RequestListener listener)
    {
        sendRequest(context, Request.SERVICES, listener);
    }

    /**
     * Requests watch service status.
     */
    public static void requestServiceStatus(Context context, ServiceId service,
                                            com.ucsf.core.services.DeviceInterface.RequestListener listener)
    {
        sendRequest(context, Request.SERVICES_STATUS, listener,
                new Entry(Messages.SERVICE_ID, service.toString())
        );
    }

    /**
     * Sends a watch service parameter to update.
     */
    public static void requestServiceParameterUpdate(final Context context, final ServiceId service,
                                                     final String parameterTag, Serializable value)
    {
        sendRequest(context, Request.PARAMETER_UPDATE,
                new RequestListener() {
                    @Override
                    public void requestProcessed(Request request, JSONObject data) throws Exception {
                        Log.d(TAG, String.format("Parameter '%s:%s' updated.",
                                service.getName(context), parameterTag));
                    }
                },
                new Entry(Messages.SERVICE_ID, service.toString()),
                new Entry(Messages.PARAMETER_ID, parameterTag),
                new Entry(Messages.VALUE, value)
        );
    }

    /**
     * Request the execution of a watch service callback.
     */
    public static void requestServiceCallbackExecution(final Context context,
                                                       final ServiceId service,
                                                       final String callbackTag)
    {
        requestServiceCallbackExecution(context, service, callbackTag, new RequestListener() {
            @Override
            public void requestProcessed(Request request, JSONObject data) throws Exception {
                Log.d(TAG, String.format("Callback '%s:%s' executed.",
                        service.getName(context), callbackTag));
            }
        });
    }

    /**
     * Request the execution of a watch service callback.
     */
    public static void requestServiceCallbackExecution(final Context context,
                                                       final ServiceId service,
                                                       final String callbackTag,
                                                       com.ucsf.core.services.DeviceInterface.RequestListener listener)
    {
        sendRequest(context, Request.CALLBACK_EXEC, listener,
                new Entry(Messages.SERVICE_ID, service.toString()),
                new Entry(Messages.PARAMETER_ID, callbackTag)
        );
    }

    /**
     * Requests the reset of the given watch service parameter
     */
    public static void requestServiceParameterReset(final Context context, final ServiceId service,
                                                    final String parameterTag)
    {
        sendRequest(context, Request.PARAMETER_RESET,
                new RequestListener() {
                    @Override
                    public void requestProcessed(Request request, JSONObject data) throws Exception {
                        Log.d(TAG, String.format("Parameter '%s:%s' is now reset.",
                                service.getName(context), parameterTag));
                    }
                },
                new Entry(Messages.SERVICE_ID, service.toString()),
                new Entry(Messages.PARAMETER_ID, parameterTag)
        );
    }

    /**
     * Creates a JSON object containing the patient information to send to the watch.
     */
    private static JSONObject getPatientInfo(Context context) {
        return JSON.create(
                new Entry(DataManager.KEY_PATIENT_ID, Settings.getCurrentUserId(context))
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StartupService.startServices(this); // Make sure that all the tables are registered
    }

    @Override
    protected void onEventReceived(Event event, JSONObject data) throws Exception {
        Sender sender = new Sender(Settings.getCurrentUserId(this), DeviceLocation.PatientWatch);
        ServerUploaderService.Provider uploader = ServerUploaderService.getProvider(this);
        switch (event) {
            case LOW_BATTERY:
                uploader.sendEvent(sender, event,
                        new Entry(UserMonitoringService.KEY_CAPACITY, data.getInt(Messages.VALUE)));
                break;
            case BATTERY_OKAY:
            case NO_WATCH:
            case NO_WATCH_ON_MORNING:
            case WATCH_OKAY:
            case PATIENT_OUTSIDE:
            case PATIENT_INSIDE:
                uploader.sendEvent(sender, event);
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
                replyToRequest(this, request, getPatientInfo(this));
                break;
            default:
                Log.w(TAG, String.format("Unexpected request '%s'!", request.getTag()));
                break;
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        // Get pending events

        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.release();


        // Make sure that the api is connected
        try (Connection connection = openConnection(this)) {
            // Loop through the events, store them, and remove across devices
            try (DataManager instance = DataManager.get(this)) {
                for (DataEvent event : events) {
                    // Pull out relevant information
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    DataMap     dataMap     = dataMapItem.getDataMap();
                    Uri         uri         = event.getDataItem().getUri();
                    String      uriPath     = uri.getPath();

                    Pair<String, Integer> p;
                    try {
                         p = parseMessagePath(uriPath);
                    } catch (Exception e) {
                        Log.e(TAG, "Invalid message path: ", e);
                        continue;
                    }

                    // Get the appropriate table
                    DataManager.Table table = DataManager.getTable(p.first);
                    if (table == null) {
                        Log.e(TAG, String.format("Invalid table name(%s).", p.first));
                        continue;
                    }

                    try {
                        // Read the entries and prepare them to be inserted into the table
                        Entry[] timestamp_entry = new Entry[1];
                        Entry[] entries         = new Entry[table.fields.length - 1];
                        String  stringValue     = null;
                        for (int i = 0, j = 0; i < table.fields.length; ++i) {
                            final DataManager.TableField field = table.fields[i];

                            if (field.tag.equals(DataManager.KEY_IS_COMMITTED)) {
                                entries[j++] = new Entry(field.tag, 0);
                                continue;
                            }
                            
                            int     idx;
                            Entry[] dst;
                            if (field.tag.equals(DataManager.KEY_TIMESTAMP)) {
                                idx = 0;
                                dst = timestamp_entry;
                            } else {
                                idx = j++;
                                dst = entries;
                            }

                            switch (field.type) {
                                case Text:
                                case UniqueText:
                                    dst[idx] = new Entry(field.tag,
                                            stringValue = dataMap.getString(field.tag));
                                    break;
                                case Integer:
                                case Boolean:
                                    dst[idx] = new Entry(field.tag, dataMap.getInt(field.tag));
                                    break;
                                case Real:
                                    dst[idx] = new Entry(field.tag, dataMap.getDouble(field.tag));
                                    break;
                                case Long:
                                    dst[idx] = new Entry(field.tag, dataMap.getLong(field.tag));
                                    break;
                                case Blob:
                                    dst[idx] = new Entry(field.tag, dataMap.getByteArray(field.tag));
                                    break;
                            }
                        }

                        // Ignore the strange appearance of null entries
                        if (stringValue == null || timestamp_entry[0] == null)
                            continue;

                        // Add entries to the table
                        table.fetchAndAdd(timestamp_entry, entries);

                        // Remove dataItem across all devices
                        removeData(connection, uri);
                    } catch (Exception e) {
                        Log.e(TAG, String.format("Failed to process entry for table '%s'",
                                table.tag), e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to receive message data: ", e);
        }
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

}
