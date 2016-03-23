package com.ucsf.wear.services;

import android.app.AlarmManager;
import android.content.Context;
import android.util.Log;

import com.ucsf.core.data.DataManager;
import com.ucsf.core.data.SharedTables;
import com.ucsf.core.services.FrequentRecurringService;
import com.ucsf.core.services.RecurringService;
import com.ucsf.core.services.ServiceId;
import com.ucsf.core.services.Services;
import com.ucsf.wear.data.Settings;

import java.util.Locale;

/**
 * Service responsible to launch all the services at startup. Check periodically if they are still
 * running.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public class StartupService extends RecurringService {
    private static final String   TAG                   = "ucsf:StartupService";
    private static       Provider mInstance             = null;
    private static       boolean  mTablesInitialized    = false;
    private static       boolean  mProvidersInitialized = false;

    /**
     * Returns the tables used by the application. If an error happens, kill the application
     * since those tables are mandatory.
     */
    public static DataManager.Table[] getApplicationTables(Context context) {
        try (DataManager instance = DataManager.get(context.getApplicationContext())) {
            return new DataManager.Table[] {
                    SharedTables.Estimote.getTable(instance),
                    SharedTables.Sensors.getTable(instance),
                    SharedTables.GroundTrust.getTable(instance),
                    SharedTables.Logs.getTable(instance)
            };
        } catch (Exception e) {
            Log.wtf(TAG, "Failed to initialize shared tables: ", e);
            System.exit(-1);
            return new DataManager.Table[0];
        }
    }

    /**
     * Makes sure that the application tables are loaded.
     */
    public static void loadTables(Context context) {
        if (mTablesInitialized)
            return;

        Locale.setDefault(Locale.US);
        for (DataManager.Table table : getApplicationTables(context))
            Log.d(TAG, String.format("Initialization of table '%s'...", table.tag));

        mTablesInitialized = true;
    }

    /**
     * Returns the providers of all the application services.
     */
    public static Services.Provider[] getApplicationProviders(Context context) {
        final Context appContext = context.getApplicationContext();
        return new Services.Provider[] {
                PhoneUploaderService.getProvider(appContext),
                SensorsService.getProvider(appContext),
                RangingService.getProvider(appContext),
                SensorTagService.getProvider(appContext),
                CleanupService.getProvider(appContext),
                PatientMonitoringService.getProvider(appContext),
                StartupService.getProvider(appContext)
        };
    }

    /**
     * Makes sure that the application providers are loaded.
     */
    public static void loadProviders(Context context) {
        if (mProvidersInitialized)
            return;

        Locale.setDefault(Locale.US);
        for (Services.Provider provider : getApplicationProviders(context))
            Log.d(TAG, String.format("Initializing provider '%s'...", provider.getServiceName()));

        mProvidersInitialized = true;
    }

    /**
     * Makes sure that the applications services are running.
     */
    public static void startServices(Context context) {
        final Context appContext = context.getApplicationContext();

        loadTables(appContext);
        loadProviders(appContext);

        if (Services.areServicesEnabled(appContext)) {
            Services.startServices(Settings.getInstance(appContext),
                    getApplicationProviders(appContext));
        } else {
            Services.startServices(Settings.getInstance(appContext),
                    CleanupService.getProvider(appContext),
                    StartupService.getProvider(appContext)
            );
        }

        // Message the current patient once to make sure it matches the one in the phone
        DeviceInterface.requestPatientInfo(appContext);
    }

    @Override
    public Provider getProvider() {
        return getProvider(getContext());
    }

    /**
     * Returns the startup service provider.
     */
    public static Provider getProvider(Context context) {
        if (mInstance == null)
            mInstance = new Provider(context);
        return mInstance;
    }

    @Override
    protected void execute(Context context) throws Exception {
        startServices(context);
    }

    /**
     * Startup service provider class.
     */
    public static class Provider extends RecurringService.Provider {
        private Provider(Context context) {
            super(context, StartupService.class, ServiceId.PW_StartupService,
                    AlarmManager.INTERVAL_HOUR);
        }
    }
}
