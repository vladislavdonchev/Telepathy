package net.hardcodes.telepathy;

/**
 * Created by MnQko on 25.1.2015 г..
 */
public final class TelepathyAPI {

    public static final String MESSAGE_UID_DELIMITER = ":";
    public static final String MESSAGE_PAYLOAD_DELIMITER = ">";

    public static final String MESSAGE_LOGIN = "login" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BIND = "bind" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BIND_ACCEPTED = "bindAccepted" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BIND_REJECTED = "bindRejected" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BIND_FAILED = "bindFailed" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_CHAT = "chat" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_VIDEO_METADATA = "videoMetadata" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_INPUT = "input" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_HEARTBEAT = "heartbeat" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_DISBAND = "disband" + MESSAGE_UID_DELIMITER;
    public static final String MESSAGE_BROADCAST = "broadcast" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_ERROR = "error" + MESSAGE_PAYLOAD_DELIMITER;
    public static final String MESSAGE_LOGOUT = "logout" + MESSAGE_UID_DELIMITER;

    public static final int ERROR_USER_ID_TAKEN = 0x1;
    public static final int ERROR_OTHER_END_HUNG_UP_UNEXPECTEDLY = 0x2;
    public static final int ERROR_OTHER_USER_BUSY = 0x3;
}