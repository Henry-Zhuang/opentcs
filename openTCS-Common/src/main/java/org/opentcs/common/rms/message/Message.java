package org.opentcs.common.rms.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.primitives.UnsignedLong;
import lombok.Data;

@Data
public abstract class Message {
  @JsonIgnore
  private Header header;

  private Integer deviceType;
  private Integer channel;
  private String type;

  private Params params;

  @Data
  public static class Header {
    private String msgSeq;
    private byte msgMode;
    private int msgLen;
    private byte[] crc;
    private byte[] reserved;

    public Header() {
    }

    public Header(String msgSeq, byte msgMode, int msgLen, byte[] crc, byte[] reserved) {
      this.msgSeq = msgSeq;
      this.msgMode = msgMode;
      this.msgLen = msgLen;
      this.crc = crc;
      this.reserved = reserved;
    }

    public enum Mode {
      ACK((byte) 1),
      NO_ACK((byte) 0);

      private final byte value;

      Mode(byte value) {
        this.value = value;
      }

      public byte getValue() {
        return value;
      }
    }
  }

  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Params {
    private Integer robotID;
    private UnsignedLong uniqueID;
  }

  public enum Channel {
    CONTROL,
    MANAGEMENT,
    DATA
  }

}
