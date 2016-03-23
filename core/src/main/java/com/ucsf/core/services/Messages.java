package com.ucsf.core.services;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Definitions of the different type of message exchanged between all the devices.
 *
 * @author  Julien Jacquemot
 * @version 1.0
 */
public abstract class Messages {
    public  static final String  SERVICE_ID           = "SERVICE_ID";
    public  static final String  PARAMETER_ID         = "PARAMETER_ID";
    public  static final String  VALUE                = "VALUE";
    private static final String  TAG                  = "ucsf:Messages";
    private static final String  REQUEST              = "REQUEST";
    private static final String  REQUEST_REPLY        = "REQUEST_REPLY";
    private static final String  REQUEST_FORMAT       = REQUEST + ":%s";
    private static final String  REQUEST_REPLY_FORMAT = REQUEST_REPLY + ":%s";
    private static final Pattern REQUEST_PATTERN;
    private static final Pattern REQUEST_REPLY_PATTERN;

    static {
        Pattern requestPattern = null;
        Pattern requestReplyPattern = null;

        try {
            requestPattern = Pattern.compile(REQUEST + ":(\\w+)");
            requestReplyPattern = Pattern.compile(REQUEST_REPLY + ":(\\w+)");
        } catch (Exception e) {
            Log.wtf(TAG, "Failed to initialize request patterns: ", e);
            System.exit(-1);
        }

        REQUEST_PATTERN = requestPattern;
        REQUEST_REPLY_PATTERN = requestReplyPattern;
    }

    /**
     * Checks if the given message tag corresponds to a request.
     */
    public static boolean isRequestMessage(String message) {
        return REQUEST_PATTERN.matcher(message).find();
    }

    /**
     * Checks if the given message tag corresponds to a request answer.
     */
    public static boolean isRequestReplyMessage(String message) {
        return REQUEST_REPLY_PATTERN.matcher(message).find();
    }

    /**
     * Returns the request corresponding to the given request message tag. Returns
     * {@link Messages.Request#INVALID} if no atch is found.
     */
    public static Request getRequestFromRequestMessage(String message) {
        Matcher matcher = REQUEST_PATTERN.matcher(message);
        if (matcher.find())
            return Request.fromTag(matcher.group(1));
        return Request.INVALID;
    }

    /**
     * Returns the request corresponding to the given request answer message tag. Returns
     * {@link Messages.Request#INVALID} if no atch is found.
     */
    public static Request getRequestFromRequestReplyMessage(String message) {
        Matcher matcher = REQUEST_REPLY_PATTERN.matcher(message);
        if (matcher.find())
            return Request.fromTag(matcher.group(1));
        return Request.INVALID;
    }

    /**
     * Enumeration of all possible events.
     */
    public enum Event implements Message {
        /** The device battery is critically low. Contains the battery level. */
        LOW_BATTERY               ("LB"),
        /** The device battery is now okay. */
        BATTERY_OKAY              ("BOK"),
        /** The patient is not wearing the watch. */
        NO_WATCH                  ("NW"),
        /** The patient is not wearing the watch (during the morning). */
        NO_WATCH_ON_MORNING       ("NWM"),
        /** The patient is now wearing the watch. */
        WATCH_OKAY                ("WO"),
        /** The patient is not at home. */
        PATIENT_OUTSIDE           ("PLH"),
        /** The patient is now at home. */
        PATIENT_INSIDE            ("PIH"),
        /** No connection between watch and phone. */
        WATCH_CONNECTION_LOST     ("WCL"),
        /** Connection between watch and phone reestablished. */
        WATCH_CONNECTION_RETRIEVED("WCR"),
        /** The patient's profile has changed. Contains the new patient unique id. */
        PROFILE_CHANGED           ("PPC"),
        /** Services need to be toggled. Contains if the services have to be enabled or not. */
        TOGGLE_SERVICES           ("TS"),
        /** Invalid event. */
        INVALID                   ("INVALID");

        private final String mTag;

        Event(String tag) {
            mTag = tag;
        }

        /**
         * Returns the event corresponding to the given tag. Returns {@link Event#INVALID} if no
         * match is found.
         */
        public static Event fromTag(String tag) {
            for (Event event : Event.values()) {
                if (event.mTag.equals(tag))
                    return event;
            }
            return INVALID;
        }

        @Override
        public String getTag() {
            return mTag;
        }
    }

    /**
     * Enumeration of all the possible request between the phone and the watch.
     */
    public enum Request implements Message {
        /** Requests the patient information, i.e. its unique id. */
        PATIENT_INFO    ("patient_info"),
        /** Requests all the available services. The answer is a list of {@link ServiceDescriptor}. */
        SERVICES        ("services"),
        /**
         * Requests a service (key: {@link Messages#SERVICE_ID})
         * {@link ServiceDescriptor.Status status}.
         * */
        SERVICES_STATUS ("services_status"),
        /**
         * Requests a service parameter update (keys: {@link Messages#SERVICE_ID},
         * {@link Messages#PARAMETER_ID}, {@link Messages#VALUE}).
         */
        PARAMETER_UPDATE("update"),
        /**
         * Requests a service parameter reset (keys: {@link Messages#SERVICE_ID},
         * {@link Messages#PARAMETER_ID}).
         */
        PARAMETER_RESET ("reset"),
        /**
         * Requests a service callback execution (keys: {@link Messages#SERVICE_ID},
         * {@link Messages#PARAMETER_ID}).
         */
        CALLBACK_EXEC   ("exec"),
        /** Ping! */
        PING            ("ping"),
        /** Invalid request. */
        INVALID         ("INVALID");

        private final String mTag;

        Request(String tag) {
            mTag = tag;
        }

        /**
         * Returns the request corresponding to the given tag or {@link Request#INVALID} if no
         * match is found.
         */
        public static Request fromTag(String tag) {
            for (Request request : Request.values()) {
                if (request.mTag.equals(tag))
                    return request;
            }
            return INVALID;
        }

        @Override
        public String getTag() {
            return mTag;
        }
    }

    /** Message interface. For now only events and requests are supported. */
    public interface Message {
        /** Returns the tag corresponding to this message. */
        String getTag();
    }

    /** Request message. */
    public static class RequestMessage implements Message {
        /** Request associated to this message. */
        public final Request request;

        public RequestMessage(Request request) {
            this.request = request;
        }

        @Override
        public String getTag() {
            return String.format(REQUEST_FORMAT, request.getTag());
        }
    }

    /** Request answer message. */
    public static class RequestReplyMessage implements Message {
        /** Request for which this answer is. */
        public final Request request;

        public RequestReplyMessage(Request request) {
            this.request = request;
        }

        @Override
        public String getTag() {
            return String.format(REQUEST_REPLY_FORMAT, request.getTag());
        }
    }

}
