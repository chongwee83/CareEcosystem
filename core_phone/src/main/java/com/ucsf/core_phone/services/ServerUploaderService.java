package com.ucsf.core_phone.services;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.ucsf.core.data.DataManager;
import com.ucsf.core.data.DeviceLocation;
import com.ucsf.core.data.Entry;
import com.ucsf.core.data.JSON;
import com.ucsf.core.data.Sender;
import com.ucsf.core.data.Timestamp;
import com.ucsf.core.services.Messages.Event;
import com.ucsf.core.services.ResponseListener;
import com.ucsf.core.services.ServiceId;
import com.ucsf.core.services.UploaderService;
import com.ucsf.core_phone.data.CaregiverInfo;

import org.json.JSONObject;


/**
 * Service responsible of pushing data daily to a remote server.
 * At startup, check if the previous commit is older than one day.
 * If it's the case, push immediately the data.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public abstract class ServerUploaderService extends UploaderService {
    protected static final String KEY_REGISTRATION_ID       = "reg_id";
    private   static final String TAG                       = "ucsf:ServerUploader";
    private   static final String SERVER_HOST               = "dev-unified-api.ucsf.edu/clinical/funcmon/1.0";
    private   static final String KEY_NEW_REGISTRATION_ID   = "new_id";
    private   static final String KEY_OLD_REGISTRATION_ID   = "old_id";
    private   static final String KEY_PASSWORD              = "password";
    private   static final String KEY_USERNAME              = "username";
    private   static final String KEY_EMAIL                 = "email";
    private   static final String KEY_PHONE_NUMBER          = "phone_number";
    private   static final String KEY_USE_EMAIL             = "use_email";
    private   static final String KEY_USE_PHONE_NUMBER      = "use_phone_number";
    private   static final String KEY_CAREGIVER_ID          = "caregiver";
    private   static final int    SERVER_TIMEOUT            = 60000;

    public static abstract class Provider extends UploaderService.Provider {
        //protected final ServerProtocol mProtocol = new ServerSecuredHttpsProtocol(SERVER_HOST);
        protected final ServerProtocol mProtocol = new ServerHttpsProtocol("198.199.116.85", 22, 8000, "root", "MoncaTLee");
        private int mIncr = 0;
        protected Provider(Context context, Class<? extends ServerUploaderService> serviceClass,
                           ServiceId service) {
            super(context, serviceClass, service, AlarmManager.INTERVAL_HALF_DAY);
        }

        public abstract ServerListenerService.Provider getServerListenerServiceProvider();

        public abstract DeviceLocation getDeviceLocation();

        @Override
        public void commit() {}

        /**
         * Send the given data to the server. Used to send short files (events, requests, config).
         */
        protected void sendData(final FileType fileType,
                                final String filename,
                                final JSONObject data,
                                final ResponseListener listener) {
            mProtocol.execute(context, new Runnable() {
                @Override
                public void run() {
                    mProtocol.writeData(fileType.toString(), filename,
                            data == null ? new byte[0] : data.toString().getBytes(), listener);
                }
            }, listener);
        }

        /**
         * Sends the given event to the server with the optional given extra data.
         */
        public void sendEvent(final Sender sender, final Event event, Entry... entries) {
            sendEvent(sender, event, new ResponseListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, String.format("Event '%s' successfully sent to the server.",
                            event));
                }

                @Override
                public void onFailure(String error, Throwable e) {
                    Log.e(TAG, String.format("Failed to send event '%s' to the server: %s",
                            event, error), e);
                }
            }, entries);
        }

        /**
         * Sends the given event to the server with the optional given extra data.
         */
        public void sendEvent(final Sender sender, final Event event,
                              ResponseListener handler, Entry... entries) {
            JSONObject data = null;
            if (entries.length > 0)
                data = JSON.create(entries);
            sendData(FileType.Event, makeServerFilename(sender, event.getTag()), data, handler);
        }

        /**
         * Sends a registration id to the server corresponding to the given sender.
         */
        public void sendRegistrationId(DeviceLocation device, String oldId, String newId,
                                       ResponseListener listener) {
            // Create the config object
            JSONObject configObject = JSON.create(
                    new Entry(KEY_NEW_REGISTRATION_ID, newId),
                    new Entry(KEY_OLD_REGISTRATION_ID, oldId)
            );

            sendData(FileType.Config,
                    makeServerFilename(new Sender("admin", device), "REGID"),
                    configObject,
                    listener
            );
        }

        /**
         * Sends to the server the given caregiver information to see if they are valid.
         */
        public void sendCaregiverProfile(final CaregiverInfo caregiver,
                                         final ServerRequestListener listener,
                                         final DeviceLocation device)
        {
            GcmInstance.getToken(this, new GcmInstance.TokenRequestListener() {
                @Override
                public void onTokenReceived(String token) {
                    sendRequest(
                            new Sender("admin", device),
                            ServerMessage.CaregiverProfileValidation,
                            listener,
                            SERVER_TIMEOUT,
                            new Entry(KEY_CAREGIVER_ID    , caregiver.caregiverId),
                            new Entry(KEY_PASSWORD        , caregiver.password),
                            new Entry(KEY_USERNAME        , caregiver.username),
                            new Entry(KEY_EMAIL           , caregiver.email),
                            new Entry(KEY_PHONE_NUMBER    , caregiver.phoneNumber),
                            new Entry(KEY_USE_EMAIL       , caregiver.acceptMail),
                            new Entry(KEY_USE_PHONE_NUMBER, caregiver.acceptSMS),
                            new Entry(KEY_REGISTRATION_ID , token)
                    );
                }
            });
        }

        /**
         * Asks the server to send the user password by email.
         */
        public void requestCaregiverPassword(final String caregiverId,
                                             final ServerRequestListener listener,
                                             final DeviceLocation device)
        {
            GcmInstance.getToken(this, new GcmInstance.TokenRequestListener() {
                @Override
                public void onTokenReceived(String token) {
                    sendRequest(
                            new Sender("admin", device),
                            ServerMessage.ForgottenPassword,
                            listener,
                            SERVER_TIMEOUT,
                            new Entry(KEY_CAREGIVER_ID   , caregiverId),
                            new Entry(KEY_REGISTRATION_ID, token)
                    );
                }
            });
        }

        /**
         * Message an account confirmation.
         */
        public void requestAccountConfirmation(final String caregiverId,
                                               final String patientId,
                                               final String password,
                                               final ServerRequestListener listener,
                                               final DeviceLocation device)
        {
            GcmInstance.getToken(this, new GcmInstance.TokenRequestListener() {
                @Override
                public void onTokenReceived(String token) {
                    sendRequest(
                            new Sender("admin", device),
                            ServerMessage.CaregiverAccountConfirmation,
                            listener,
                            SERVER_TIMEOUT,
                            new Entry(KEY_CAREGIVER_ID          , caregiverId),
                            new Entry(DataManager.KEY_PATIENT_ID, patientId),
                            new Entry(KEY_PASSWORD              , password),
                            new Entry(KEY_REGISTRATION_ID       , token)
                    );
                }
            });
        }

        /**
         * Message to the server if the given caregiver can monitor the given patient.
         */
        public void requestPatientAuthorization(final String caregiverId,
                                                final String patientId,
                                                final ServerRequestListener listener,
                                                final DeviceLocation device)
        {
            GcmInstance.getToken(this, new GcmInstance.TokenRequestListener() {
                @Override
                public void onTokenReceived(String token) {
                    sendRequest(
                            new Sender("admin", device),
                            ServerMessage.PatientAuthorization,
                            listener,
                            SERVER_TIMEOUT,
                            new Entry(DataManager.KEY_PATIENT_ID, patientId),
                            new Entry(KEY_CAREGIVER_ID          , caregiverId),
                            new Entry(KEY_REGISTRATION_ID       , token)
                    );
                }
            });
        }

        /**
         * Makes a valid filename for server parsing using the following format:
         * 99000213875160_20140805-132705_MM_ACC.txt, where
         * - 99000213875160 is the sender ID,
         * - 20140805-132705 is the timestamp in the format: YYYYmmdd-HHMMSS
         * - MM is the origin of the file, this will either be MM for mobile monitoring or HM for home monitoring (aka watch data)
         * ACC is the type of data, right now this is either ACC or GPS and in the future would also be RSSI.
         */
        protected String makeServerFilename(Sender sender, String tag) {
            // Get the formatted timestamp
            String timestamp = Timestamp.getTimestamp(Timestamp.Format.YYYYMMDD_HHMMSS);

            return String.format("%s_%s_%s_%s_%d", sender.id, timestamp, sender.location.toString(),
                    tag, mIncr++);
        }

        /**
         * Send a request to the server.
         */
        public void sendRequest(Sender sender,
                                final ServerMessage request,
                                final ServerRequestListener listener,
                                int timeoutMillis,
                                Entry... entries) {
            // Create the JSON object to send
            JSONObject data = null;
            if (entries.length > 0)
                data = JSON.create(entries);

            // Prepare and send the request
            RequestHandler handler = new RequestHandler(this, request, listener);
            listener.onDeliveryStart();
            getServerListenerServiceProvider().registerListener(request, handler);
            sendData(FileType.Request, makeServerFilename(sender, request.tag), data, handler);
            handler.postDelayed(handler, timeoutMillis);
        }

        private static class RequestHandler extends Handler
                implements ResponseListener, ServerListener, Runnable
        {
            private final Provider              mProvider;
            private final ServerRequestListener mRequestListener;
            private final ServerMessage         mExpectedMessage;

            public RequestHandler(Provider provider, ServerMessage expectedMessage,
                                  ServerRequestListener listener)
            {
                super(provider.context.getMainLooper());
                mProvider        = provider;
                mRequestListener = listener;
                mExpectedMessage = expectedMessage;
            }

            @Override
            public void onServerMessage(ServerMessage type, Bundle data) {
                if (type == mExpectedMessage) {
                    removeCallbacks(this);
                    mRequestListener.onResponseReceived(data);
                    mProvider.getServerListenerServiceProvider().unregisterListener(this);
                }
            }

            @Override
            public void onSuccess() {}

            @Override
            public void onFailure(String error, Throwable e) {
                removeCallbacks(this);
                mProvider.getServerListenerServiceProvider().unregisterListener(this);
                mRequestListener.onDeliveryFailure(error, e);
            }

            @Override
            public void run() {
                mProvider.getServerListenerServiceProvider().unregisterListener(this);
                mRequestListener.onTimeout();
            }
        }

    }
}
