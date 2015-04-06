package net.hardcodes.telepathy.model;

/**
 * Created by MnQko on 25.1.2015 г..
 */
public final class TelepathyAPI {

    public static final String MESSAGE_UID_DELIMITER = ":";
    public static final String MESSAGE_PAYLOAD_DELIMITER = ">";

    public static final String MESSAGE_REGISTER = "registerAccount" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_REGISTRATION_SUCCESS = "registerAccountSuccess" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_LOGIN = "login" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_LOGIN_SUCCESS = "loginSuccess" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BIND = "bind" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BIND_ACCEPTED = "bindAccepted" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BIND_REJECTED = "bindRejected" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_CHAT = "chat" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_VIDEO_METADATA = "videoMetadata" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_INPUT = "input" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_HEARTBEAT = "heartbeat" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_DISBAND = "disband" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BROADCAST = "broadcast" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_ERROR = "error" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_LOGOUT = "releaseConnection" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_LOGOUT_SUCCESS = "releaseConnectionSuccess" + MESSAGE_UID_DELIMITER;

    public static final int ERROR_USER_ID_TAKEN = 0x0;
    public static final int ERROR_USER_AUTHENTICATION_FAILED = 0x1;
    public static final int ERROR_OTHER_END_HUNG_UP_UNEXPECTEDLY = 0x2;
    public static final int ERROR_OTHER_USER_BUSY = 0x3;
    public static final int ERROR_BIND_FAILED = 0x4;
    public static final int ERROR_SERVER_OVERLOADED = 0x5;
}