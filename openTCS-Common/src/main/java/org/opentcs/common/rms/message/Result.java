package org.opentcs.common.rms.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Result extends Message {
    private ResultParams params;

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class ResultParams extends Params {
        private String barcode;
        private Integer errorCode;
        private Integer errorReason;
    }

    public enum ErrorCode {
      SUCCEED,
      FAIL
    }

    public enum ErrorReason {
      NONE(0),
      OTHER_REASON(100);

      private final int value;
      ErrorReason(int value) {
        this.value = value;
      }

      public int getValue() {
        return value;
      }
    }
}
