package com.ucsf.core.data;

import android.content.Context;

import java.io.Serializable;

/**
 * Interface to access the application parameters.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public abstract class Settings {
    private static final String                      TAG            = "ucsf:Settings";
    private static final String                      KEY_PARAMETER  = "parameter";
    private static final String                      KEY_VALUE      = "value";
    private static final PersistentParameter<String> mCurrentUserId =
            new PersistentParameter<>("user", "");
    private static       DataManager.Table           mSettingsTable = null;
    public         final Context                     context;

    protected Settings(Context context) {
        this.context = context.getApplicationContext();
    }

    protected static DataManager.Table getTable(DataManager instance) throws Exception {
        if (mSettingsTable == null)
            mSettingsTable = instance.createTable(
                    "settings",
                    DeviceLocation.Unknown,
                    new DataManager.TableField(KEY_PARAMETER, DataManager.Type.UniqueText),
                    new DataManager.TableField(KEY_VALUE, DataManager.Type.Blob)
            );
        return mSettingsTable;
    }

    /**
     * Loads the parameter identified by the given unique id. If the parameter is not found, returns
     * the given default value.
     */
    public static Serializable loadParameter(Context context, String tag, Serializable defaultValue)
            throws Exception
    {
        try (DataManager instance = DataManager.get(context)) {
            DataManager.Cursor cursor = getTable(instance).fetch(new String[]{KEY_VALUE},
                    new DataManager.Condition.Equal<>(KEY_PARAMETER, tag));
            if (cursor != null && cursor.moveToFirst())
                return cursor.getSerializable(KEY_VALUE);
            return defaultValue;
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Savse the parameter identified by the given unique id. The value to insert will be serialized
     * in a bytes array, so any kind of serializable object can be stored this way.
     */
    public static boolean saveParameter(Context context, String tag, Serializable value)
            throws Exception
    {
        try (DataManager instance = DataManager.get(context)) {
            return getTable(instance).fetchAndAdd(
                    new Entry[]{
                            new Entry(KEY_PARAMETER, tag)
                    },
                    new Entry(KEY_VALUE, value, true)
            );
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Returns the unique user id for which this application is set for.
     */
    public static String getCurrentUserId(Context context) {
        return mCurrentUserId.get(context);
    }

    /**
     * Binds this application to the user identified by the given unique id.
     */
    public static void setCurrentUserId(Context context, String patientId) {
        mCurrentUserId.set(context, patientId);
    }

    /**
     * Returns the table storing the application warnings and errors logs.
     */
    public abstract DataManager.Table getLogsTable(DataManager instance) throws Exception;

}
