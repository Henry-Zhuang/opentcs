package org.opentcs.virtualvehicle.rms;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines some constants used for socket clients.
 */
public class SocketConstants {
    public static final Map<Integer, String[]> COMMAND_DICT = new HashMap<Integer, String[]>() {{
        put(0, new String[]{"pick", "place", "move", "joint", "cancel", "pause", "continue", "charge"});
        put(1, new String[]{"online", "offline", "shutdown"});
        put(2, new String[]{"heartbeat", "statistics"});
    }};

    public static final String PACK_FMT_STR = "<6B32sBL2s2s";

    public static final int PACKET_HEADER_LEN = 47;

    public static final String TCP_SERVER_IP = "127.0.0.1";

    public static final int TCP_SERVER_PORT = 21129;

    public static final String[] IMMEDIATE_COMMAND = {"cancel", "pause", "continue"};

    public static final int CONNECT_TIMEOUT = 10;

    public static final int SEND_TIMEOUT = 2;

    public static final int ACK_TIMEOUT = 2;

    public static final int HEARTBEAT_INTERVAL_MILLIS = 1000;

    public static final int RESEND_INTERVAL_MILLIS = 1000;
}
