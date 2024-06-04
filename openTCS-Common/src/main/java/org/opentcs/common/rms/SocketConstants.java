package org.opentcs.common.rms;

import org.opentcs.common.rms.message.Command;

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

  public static final String PROPERTY_KEY_SERVER_IP = "socket:serverIp";

  public static final String PROPERTY_KEY_SERVER_PORT = "socket:serverPort";

  public static final String DEFAULT_SERVER_IP = "127.0.0.1";

  public static final int DEFAULT_SERVER_PORT = 21129;

  public static final Command.Type[] INSTANT_COMMAND = {
      Command.Type.CANCEL,
      Command.Type.PAUSE,
      Command.Type.CONTINUE,
      Command.Type.OFFLINE,
      Command.Type.ONLINE,
      Command.Type.SHUTDOWN,
      Command.Type.TURN_OFF_ALARM
  };

  public static final int CONNECT_TIMEOUT = 10;

  public static final int SEND_TIMEOUT = 2;

  public static final int ACK_TIMEOUT = 2;

  public static final int HEARTBEAT_INTERVAL_MILLIS = 100;

  public static final int RESEND_INTERVAL_MILLIS = 1000;
}
