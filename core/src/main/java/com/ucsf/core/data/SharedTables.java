package com.ucsf.core.data;

import com.ucsf.core.services.UploaderService;

/**
 * List of all the tables that the patient's watch can send to the patient's phone. Allows the phone
 * to have the definition of such tables.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public abstract class SharedTables {
    /**
     * Table storing data coming from the patient's watch {@link android.hardware.Sensor sensors}.
     * The same table definition can be use for other Android devices. Fields are:                  <br/>
     * - {@link DataManager#KEY_TIMESTAMP}      [TEXT]   : Time of the acquisition                  <br/>
     * - {@link DataManager#KEY_PATIENT_ID}     [TEXT]   : Unique id of the patient                 <br/>
     * - {@link DataManager#KEY_IS_COMMITTED}   [BOOLEAN]: Indicates if the entry has been
     *                                                     synchronized with a remote device        <br/>
     * - {@link Sensors#KEY_ACC_X}              [REAL]   : X component of the device acceleration   <br/>
     * - {@link Sensors#KEY_ACC_Y}              [REAL]   : Y component of the device acceleration   <br/>
     * - {@link Sensors#KEY_ACC_Z}              [REAL]   : Z component of the device acceleration   <br/>
     * - {@link Sensors#KEY_AZIMUTH}            [REAL]   : Azimuth component of the device
     *                                                     orientation                              <br/>
     * - {@link Sensors#KEY_PITCH}              [REAL]   : Pitch component of the device orientation<br/>
     * - {@link Sensors#KEY_ROLL}               [REAL]   : roll component of the device orientation <br/>
     * - {@link Sensors#KEY_HEART_RATE}         [REAL]   : Heart rate of the user. Not supported.   <br/>
     * - {@link Sensors#KEY_IS_HEART_RATE_VALID}[BOOLEAN]: Indicates if the heart rate data are
     *                                                     valid                                    <br/>
     * - {@link Sensors#KEY_STEP_COUNT}         [INTEGER]: Number of steps since the last entry
     */
    public static abstract class Sensors {
        public static final String KEY_ACC_X               = "acc_x";
        public static final String KEY_ACC_Y               = "acc_y";
        public static final String KEY_ACC_Z               = "acc_z";
        public static final String KEY_AZIMUTH             = "azimuth";
        public static final String KEY_PITCH               = "pitch";
        public static final String KEY_ROLL                = "roll";
        public static final String KEY_HEART_RATE          = "heart_rate";
        public static final String KEY_IS_HEART_RATE_VALID = "is_heart_rate_valid";
        public static final String KEY_STEP_COUNT          = "step_count";

        private static DataManager.Table mTable;

        /** Returns the patient's watch sensor table. */
        public static DataManager.Table getTable(DataManager instance) throws Exception {
            if (mTable == null)
                mTable = UploaderService.addTable(
                        instance,
                        "sensors",
                        DeviceLocation.PatientWatch,
                        new DataManager.TableField(DataManager.KEY_TIMESTAMP, DataManager.Type.Text),
                        new DataManager.TableField(DataManager.KEY_PATIENT_ID, DataManager.Type.Text),
                        new DataManager.TableField(DataManager.KEY_IS_COMMITTED, DataManager.Type.Boolean, 0),
                        new DataManager.TableField(KEY_ACC_X, DataManager.Type.Real),
                        new DataManager.TableField(KEY_ACC_Y, DataManager.Type.Real),
                        new DataManager.TableField(KEY_ACC_Z, DataManager.Type.Real),
                        new DataManager.TableField(KEY_AZIMUTH, DataManager.Type.Real),
                        new DataManager.TableField(KEY_PITCH, DataManager.Type.Real),
                        new DataManager.TableField(KEY_ROLL, DataManager.Type.Real),
                        new DataManager.TableField(KEY_HEART_RATE, DataManager.Type.Real),
                        new DataManager.TableField(KEY_IS_HEART_RATE_VALID, DataManager.Type.Boolean, 0),
                        new DataManager.TableField(KEY_STEP_COUNT, DataManager.Type.Integer)
                );
            return mTable;
        }

        /** Class storing {@link android.hardware.Sensor sensors} data. */
        public static class Data {
            public float[] acceleration     = new float[3];  /**< Acceleration of the device in m.s^-2. */
            public float[] orientation      = new float[3];  /**< Orientation of the device in degrees. */
            public float   heartRate        = -1;            /**< Heart rate in beats per minute. Not supported. */
            public boolean isHeartRateValid = false;         /**< Indicates if heart rate data are valid. */
            public int     stepCount        = 0;             /**< Number of steps since the last acquisition. */
        }
    }

    /**
     * Table storing data coming from Estimote bluetooth beacons. Fields are:                       <br/>
     * - {@link DataManager#KEY_TIMESTAMP}      [TEXT]   : Time of the acquisition                  <br/>
     * - {@link DataManager#KEY_PATIENT_ID}     [TEXT]   : Unique id of the patient                 <br/>
     * - {@link DataManager#KEY_IS_COMMITTED}   [BOOLEAN]: Indicates if the entry has been
     *                                                     synchronized with a remote device        <br/>
     * - {@link Estimote#KEY_RSSI}              [BLOB]   : RSSI values
     */
    public static abstract class Estimote {
        public static final String KEY_RSSI = "rssi";

        private static DataManager.Table mTable;

        /** Returns the RSSI values table. */
        public static DataManager.Table getTable(DataManager instance) throws Exception {
            if (mTable == null)
                mTable = UploaderService.addTable(
                        instance,
                        "estimote",
                        DeviceLocation.PatientWatch,
                        new DataManager.TableField(DataManager.KEY_PATIENT_ID, DataManager.Type.Text),
                        new DataManager.TableField(DataManager.KEY_TIMESTAMP, DataManager.Type.Text),
                        new DataManager.TableField(DataManager.KEY_IS_COMMITTED, DataManager.Type.Boolean, 0),
                        new DataManager.TableField(KEY_RSSI, DataManager.Type.Blob)
                );
            return mTable;
        }
    }

    /**
     * Table storing ground trust data, i.e. a data category, a label and time range.
     * Fields are:                                                                                  <br/>
     * - {@link DataManager#KEY_PATIENT_ID}     [TEXT]   : Unique id of the patient                 <br/>
     * - {@link DataManager#KEY_IS_COMMITTED}   [BOOLEAN]: Indicates if the entry has been
     *                                                     synchronized with a remote device        <br/>
     * - {@link GroundTrust#KEY_TYPE}           [TEXT]   : Type of data we want to label            <br/>
     * - {@link GroundTrust#KEY_LABEL}          [TEXT]   : Label of the data                        <br/>
     * - {@link GroundTrust#KEY_START}          [TEXT]   : Timestamp corresponding to the start of
     *                                                     the acquisition.                         <br/>
     * - {@link GroundTrust#KEY_END}            [TEXT]   : Timestamp corresponding to the end of
     *                                                     the acquisition.                         <br/>
     */
    public static abstract class GroundTrust {
        public static final String KEY_TYPE  = "type";
        public static final String KEY_LABEL = "label";
        public static final String KEY_START = "start";
        public static final String KEY_END   = "end";

        private static DataManager.Table mTable;

        /** Returns the ground trust table. */
        public static DataManager.Table getTable(DataManager instance) throws Exception {
            if (mTable == null)
                mTable = UploaderService.addTable(
                        instance,
                        "ground_trust",
                        DeviceLocation.PatientPhone,
                        new DataManager.TableField(DataManager.KEY_PATIENT_ID, DataManager.Type.Text),
                        new DataManager.TableField(DataManager.KEY_IS_COMMITTED, DataManager.Type.Boolean, 0),
                        new DataManager.TableField(KEY_TYPE, DataManager.Type.Text),
                        new DataManager.TableField(KEY_LABEL, DataManager.Type.Text),
                        new DataManager.TableField(KEY_START, DataManager.Type.Text),
                        new DataManager.TableField(KEY_END, DataManager.Type.Text)
                );
            return mTable;
        }
    }

    /**
     * Table storing warnings and errors logs. Fields are:                                          <br/>
     * - {@link DataManager#KEY_TIMESTAMP}      [TEXT]   : Time of the acquisition                  <br/>
     * - {@link DataManager#KEY_PATIENT_ID}     [TEXT]   : Unique id of the patient                 <br/>
     * - {@link DataManager#KEY_IS_COMMITTED}   [BOOLEAN]: Indicates if the entry has been
     *                                                     synchronized with a remote device        <br/>
     * - {@link Logs#KEY_LOG}                   [TEXT]   : Content of the log
     */
    public static abstract class Logs {
        public static final String KEY_LOG = "log";

        private static DataManager.Table mTable;

        /** Returns the warnings and errors logs table. */
        public static DataManager.Table getTable(DataManager instance) throws Exception {
            if (mTable == null)
                mTable = UploaderService.addTable(
                        instance,
                        "logs",
                        DeviceLocation.PatientWatch,
                        new DataManager.TableField(DataManager.KEY_PATIENT_ID, DataManager.Type.Text),
                        new DataManager.TableField(DataManager.KEY_TIMESTAMP, DataManager.Type.Text),
                        new DataManager.TableField(DataManager.KEY_IS_COMMITTED, DataManager.Type.Boolean, 0),
                        new DataManager.TableField(KEY_LOG, DataManager.Type.Text)
                );
            return mTable;
        }

    }
}
