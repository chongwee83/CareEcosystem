package com.ucsf.services;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.ucsf.R;
import com.ucsf.core.data.DataManager;
import com.ucsf.core.data.DataManager.Condition;
import com.ucsf.core.data.DeviceLocation;
import com.ucsf.core.data.Entry;
import com.ucsf.core.data.RSSI;
import com.ucsf.core.data.Sender;
import com.ucsf.core.data.SharedTables;
import com.ucsf.core.data.Timestamp;
import com.ucsf.core.services.Annotations;
import com.ucsf.core.services.ResponseListener;
import com.ucsf.core.services.ServiceId;
import com.ucsf.core_phone.services.FileType;
import com.ucsf.core_phone.services.GcmInstance;
import com.ucsf.data.PatientProfile;
import com.ucsf.data.PatientProfile.Room;
import com.ucsf.data.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible of pushing data daily to a remote server.
 * At startup, check if the previous commit is older than one day.
 * If it's the case, push immediately the data.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public class ServerUploaderService extends com.ucsf.core_phone.services.ServerUploaderService {
    private static final String   TAG            = "ucsf:ServerUploader";
    private static final String   KEY_PUSH_DATA  = "a";
    private static final int      MAX_ENTRIES    = 1000;
    private static       Provider mInstance      = null;

    /**
     * Retunrs the service provider.
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
     * Class describing the entry to push to the server. Basically parse database entries to a
     * string representation.
     */
    private static class TableEntry {
        /** Database {@link DataManager.Cursor cursor} */
        public  final DataManager.Cursor cursor;

        /** Patient unique identifier. */
        public  final String             patientId;

        /** Unique identifier of the data type. */
        public        String             tag;

        private final StringBuffer       mBuffer;

        public TableEntry(DataManager.Cursor cursor, String patientId) {
            this.cursor = cursor;
            this.mBuffer = new StringBuffer();
            this.patientId = patientId;
        }

        /** Add an entry to the underlying buffer. */
        public void addLine(String line) {
            mBuffer.append(line);
            mBuffer.append('\n');
        }

        /** Returns all the entries previously stored to be sent to the server. */
        public String getContent() {
            return mBuffer.toString();
        }
    }

    /**
     * Server uploader service provider class.
     */
    public static class Provider extends
            com.ucsf.core_phone.services.ServerUploaderService.Provider
    {
        private Provider(Context context) {
            super(context, com.ucsf.services.ServerUploaderService.class, ServiceId.PP_ServerUploaderService);
            addCallback("PUSH_DATA", R.string.action_push_data, KEY_PUSH_DATA);
        }

        @Override
        public ServerListenerService.Provider getServerListenerServiceProvider() {
            return ServerListenerService.getProvider(context);
        }

        @Override
        public DeviceLocation getDeviceLocation() {
            return DeviceLocation.PatientPhone;
        }

        @Override
        public void commit() {
            commit(new ResponseListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Database content successfully sent to the server.");
                }

                @Override
                public void onFailure(String error, Throwable e) {
                    Log.e(TAG, "Failed to send database content to the server: " + error, e);
                }
            });
        }

        /**
         * Sends the given patient profile to the server.
         */
        public void sendProfile(final PatientProfile profile, final ResponseListener listener) {
            GcmInstance.getToken(this, new GcmInstance.TokenRequestListener() {
                @Override
                public void onTokenReceived(String token) {
                    JSONObject configObject;
                    try {
                        JSONArray roomsObject = new JSONArray();
                        for (int i = 0; i < profile.rooms.length; ++i) {
                            final Room room = profile.rooms[i];
                            JSONObject roomObject = new JSONObject();
                            roomObject.put(PatientProfile.KEY_ROOM_NAME       , room.getRoomName());
                            roomObject.put(PatientProfile.KEY_ROOM_IDX        , i);
                            roomObject.put(PatientProfile.KEY_MOTE_ID         , room.getMoteId());
                            roomObject.put(PatientProfile.KEY_CEILING_HEIGHT  , room.getHeight());
                            roomObject.put(PatientProfile.KEY_FLOOR           , room.getFloor());
                            roomObject.put(PatientProfile.KEY_X_DIST_FROM_PREV, room.getXDistanceFromPrevious());
                            roomObject.put(PatientProfile.KEY_Y_DIST_FROM_PREV, room.getYDistanceFromPrevious());

                            roomsObject.put(roomObject);
                        }

                        configObject = new JSONObject();
                        configObject.put(DataManager.KEY_PATIENT_ID        , profile.patientId);
                        configObject.put(PatientProfile.KEY_REGISTRATION_ID, token);
                        configObject.put(PatientProfile.KEY_USERNAME       , profile.username);
                        configObject.put(PatientProfile.KEY_TALLEST_CEILING, profile.tallestCeilingHeight);
                        configObject.put(PatientProfile.KEY_HOME_LATITUDE  , profile.homeLatitude);
                        configObject.put(PatientProfile.KEY_HOME_LONGITUDE , profile.homeLongitude);
                        configObject.put(PatientProfile.KEY_ROOMS          , roomsObject);
                        configObject.put(PatientProfile.KEY_START_TIMESTAMP, parseTimestamp(profile.setupStartTimestamp));
                        configObject.put(PatientProfile.KEY_END_TIMESTAMP  , parseTimestamp(profile.setupEndTimestamp));
                    } catch (Exception e) {
                        listener.onFailure("Failed to create configuration object: ", e);
                        return;
                    }

                    Sender sender   = new Sender(profile.patientId, DeviceLocation.PatientPhone);
                    String filename = makeServerFilename(sender, PatientProfile.KEY_PROFILE);
                    sendData(FileType.Config, filename, configObject, listener);
                }
            });
        }

        /**
         * Pushes the data to the server and notify the user.
         */
        @Annotations.MappedMethod(KEY_PUSH_DATA)
        public void pushData() {
            Toast.makeText(context, R.string.toast_pushing_data, Toast.LENGTH_LONG).show();

            commit(new ResponseListener() {
                final Handler mHandler = new Handler();

                @Override
                public void onSuccess() {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, R.string.toast_pushed_data,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onFailure(String error, Throwable e) {
                    Log.e(TAG, "Failed to send database content to the server: " + error, e);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, R.string.toast_push_error,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }

        /**
         * Sends the uncommitted database entries to the server.
         */
        public void commit(final ResponseListener listener) {
            mProtocol.execute(context, new Runnable() {
                @Override
                public void run() {
                    final EntryResponseHandler entryHandler = new EntryResponseHandler(listener);

                    Condition[] conditions = new Condition[3];
                    conditions[0] =
                            new Condition.Equal<>(DataManager.KEY_IS_COMMITTED, 0);
                    Condition timestampCond =
                            new Condition.LessEqual<>(DataManager.KEY_TIMESTAMP,
                                    Timestamp.getTimestamp());
                    Condition startCond =
                            new Condition.LessEqual<>(SharedTables.GroundTrust.KEY_START,
                                    Timestamp.getTimestamp());

                    try (DataManager instance = DataManager.get(context)) {
                        // Iterate through each tables and patients
                        StartupService.loadTables(context);
                        for (DataManager.Table table : getMonitoredTables()) {
                            FileType fileType = getFileType(table);
                            conditions[1] = table == SharedTables.GroundTrust.getTable(instance) ?
                                    startCond : timestampCond;

                            for (String profile : Settings.getPatientIDs(context)) {
                                conditions[2] = new Condition.Equal<>(DataManager.KEY_PATIENT_ID, profile);
                                DataManager.Cursor cursor;
                                while ((cursor = table.fetch(MAX_ENTRIES, conditions)) != null
                                        && cursor.moveToFirst()) {
                                    // Parse the table entry
                                    TableEntry entry = parseTableEntry(instance, table, cursor, profile);

                                    // Push the entry to the server, if the file being uploaded is ground truth,
                                    // upload to the config directory on the server
                                    mProtocol.writeData(
                                            fileType.toString(),
                                            makeServerFilename(new Sender(profile, table.location), entry.tag),
                                            entry.getContent().getBytes(),
                                            entryHandler.create(table, conditions)
                                    );
                                }
                            }
                        }
                    } catch (Exception e) {
                        listener.onFailure("Failed to commit table content", e);
                    } finally {
                        entryHandler.close();
                    }
                }
            }, listener);
        }

        /**
         * Returns the server folder to use for the given table entries.
         */
        private FileType getFileType(DataManager.Table table) throws Exception {
            try (DataManager instance = DataManager.get(context)) {
                if (table == SharedTables.GroundTrust.getTable(instance))
                    return FileType.Config;
            }
            return FileType.Data;
        }

        /**
         * Parses the given timestamp to the format expected by the server.
         */
        private String parseTimestamp(String timestamp) {
            Calendar calendar =
                    Timestamp.getCalendarFromTimestamp(timestamp);
            return Timestamp.getTimestampFromCalendar(calendar, Timestamp.Format.YY_MM_DDTHH_MM_SS_MS);
        }

        /**
         * Parses the timestamp of the entry to which point the given cursor to the format expected
         * by the server.
         */
        private String parseTimestamp(DataManager.Cursor cursor) {
            return parseTimestamp(cursor, DataManager.KEY_TIMESTAMP);
        }

        /**
         * Parses the timestamp of the entry to which point the given cursor to the format expected
         * by the server.
         * @param key By default, the entry timestamp is referenced under the key
         *            {@link DataManager#KEY_TIMESTAMP}, but if it's not the case, you can precise
         *            the timestamp key here.
         */
        private String parseTimestamp(DataManager.Cursor cursor, String key) {
            return parseTimestamp(cursor.getString(key));
        }

        /**
         * Parses the given estimote data to a valid server format.
         */
        private void parseEstimoteData(TableEntry entry) {
            PatientProfile profile = Settings.getPatientProfile(context, entry.patientId);
            entry.tag = "RSSI";

            // Create the header
            StringBuilder header = new StringBuilder();
            header.append("timestamp[s] mote_count[i]");
            for (int i = 0; i < profile.rooms.length; ++i)
                header.append(" room_").append(i).append("[s][]")
                        .append(" value_").append(i).append("[f]");
            entry.addLine(header.toString());

            do {
                RSSI rssi = new RSSI((HashMap<String, Double>)
                        entry.cursor.getSerializable(SharedTables.Estimote.KEY_RSSI));
                StringBuilder line = new StringBuilder();

                line.append(parseTimestamp(entry.cursor));
                line.append(" ");
                line.append(profile.rooms.length);
                for (Room room : profile.rooms)
                    line.append(" [").append(room.getRoomName()).append("] ")
                            .append(rssi.get(room));

                entry.addLine(line.toString());
            } while (entry.cursor.moveToNext());
        }

        /**
         * Parses the given ground trust data to a valid server format.
         */
        private void parseGroundTrustData(TableEntry entry) {
            entry.tag = "GT";

            // Create the header
            entry.addLine(String.format("%s[s] %s[s][] %s[s] %s[s]",
                            SharedTables.GroundTrust.KEY_TYPE,
                            SharedTables.GroundTrust.KEY_LABEL,
                            SharedTables.GroundTrust.KEY_START,
                            SharedTables.GroundTrust.KEY_END)
            );

            do {
                entry.addLine(String.format("%s [%s] %s %s",
                        entry.cursor.getString(SharedTables.GroundTrust.KEY_TYPE),
                        entry.cursor.getString(SharedTables.GroundTrust.KEY_LABEL),
                        parseTimestamp(entry.cursor, SharedTables.GroundTrust.KEY_START),
                        parseTimestamp(entry.cursor, SharedTables.GroundTrust.KEY_END)
                ));
            } while (entry.cursor.moveToNext());
        }

        /**
         * Parses the given sensors data to a valid server format.
         */
        private void parseSensorsData(TableEntry entry) {
            entry.tag = "ACC";

            // Create the header
            entry.addLine(String.format("%s[s] %s[f] %s[f] %s[f] %s[f] %s[f] %s[f] %s[f] %s[i] %s[i]",
                    DataManager.KEY_TIMESTAMP,
                    SharedTables.Sensors.KEY_ACC_X,
                    SharedTables.Sensors.KEY_ACC_Y,
                    SharedTables.Sensors.KEY_ACC_Z,
                    SharedTables.Sensors.KEY_AZIMUTH,
                    SharedTables.Sensors.KEY_PITCH,
                    SharedTables.Sensors.KEY_ROLL,
                    SharedTables.Sensors.KEY_HEART_RATE,
                    SharedTables.Sensors.KEY_IS_HEART_RATE_VALID,
                    SharedTables.Sensors.KEY_STEP_COUNT
            ));

            do {
                entry.addLine(String.format("%s %f %f %f %f %f %f %f %d %d",
                        parseTimestamp(entry.cursor),
                        entry.cursor.getDouble(SharedTables.Sensors.KEY_ACC_X),
                        entry.cursor.getDouble(SharedTables.Sensors.KEY_ACC_Y),
                        entry.cursor.getDouble(SharedTables.Sensors.KEY_ACC_Z),
                        entry.cursor.getDouble(SharedTables.Sensors.KEY_AZIMUTH),
                        entry.cursor.getDouble(SharedTables.Sensors.KEY_PITCH),
                        entry.cursor.getDouble(SharedTables.Sensors.KEY_ROLL),
                        entry.cursor.getDouble(SharedTables.Sensors.KEY_HEART_RATE),
                        entry.cursor.getInt(SharedTables.Sensors.KEY_IS_HEART_RATE_VALID),
                        entry.cursor.getInt(SharedTables.Sensors.KEY_STEP_COUNT)
                ));
            } while (entry.cursor.moveToNext());
        }

        /**
         * Parses the given sensors data to a valid server format.
         */
        private void parseGPSData(TableEntry entry) {
            entry.tag = "GPS";

            // Create the header
            entry.addLine(String.format("%s[s] %s[f] %s[f]",
                    DataManager.KEY_TIMESTAMP,
                    GPSLocationService.KEY_LATITUDE,
                    GPSLocationService.KEY_LONGITUDE
            ));

            do {
                entry.addLine(String.format("%s %f %f",
                        parseTimestamp(entry.cursor),
                        entry.cursor.getDouble(GPSLocationService.KEY_LATITUDE),
                        entry.cursor.getDouble(GPSLocationService.KEY_LONGITUDE)
                ));
            } while (entry.cursor.moveToNext());
        }

        /**
         * Parses the given log entry to a valid server format.
         */
        private void parseLogs(TableEntry entry) {
            entry.tag = "LOG";
            do {
                entry.addLine(String.format("%s",
                        entry.cursor.getString(SharedTables.Logs.KEY_LOG)
                ));
            } while (entry.cursor.moveToNext());
        }

        /**
         * Parses the given table entry.
         */
        private TableEntry parseTableEntry(DataManager instance, DataManager.Table table,
                                           DataManager.Cursor cursor, String patientId)
                throws Exception
        {
            TableEntry entry = new TableEntry(cursor, patientId);

            if (table == SharedTables.Estimote.getTable(instance))
                parseEstimoteData(entry);
            else if (table == SharedTables.Sensors.getTable(instance) ||
                    table == SensorsService.getTable(instance))
                parseSensorsData(entry);
            else if (table == GPSLocationService.getTable(instance))
                parseGPSData(entry);
            else if (table == SharedTables.GroundTrust.getTable(instance))
                parseGroundTrustData(entry);
            else if (table == SharedTables.Logs.getTable(instance) ||
                    table == Settings.getPhoneLogsTable(instance))
                parseLogs(entry);
            else
                Log.e(TAG, String.format("Table '%s' not handled!", table.tag));

            return entry;
        }

        private static class EntryResponseHandler {
            private final AtomicInteger mPendingEntries = new AtomicInteger(1);
            private final ResponseListener mListener;
            private String mLastError = null;
            private Throwable mLastThrowable;

            public EntryResponseHandler(ResponseListener listener) {
                mListener = listener;
            }

            public ResponseListener create(final DataManager.Table table,
                                           DataManager.Condition... conditions) {
                mPendingEntries.incrementAndGet();
                final Condition entryConditions[] = new Condition[]
                        {conditions[0], conditions[1], conditions[2]};

                return new ResponseListener() {
                    @Override
                    public void onSuccess() {
                        try {
                            table.update(new Entry[]{
                                    new Entry(DataManager.KEY_IS_COMMITTED, 1)
                            }, entryConditions);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to update database: ", e);
                        } finally {
                            close();
                        }
                    }

                    @Override
                    public void onFailure(String error, Throwable e) {
                        mLastError = error;
                        mLastThrowable = e;
                        close();
                    }
                };
            }

            public void close() {
                if (mPendingEntries.decrementAndGet() <= 0) {
                    if (mLastError == null)
                        mListener.onSuccess();
                    else
                        mListener.onFailure(mLastError, mLastThrowable);
                }
            }
        }
    }
}
