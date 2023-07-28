package org.opentcs.kernel.vehicles.rms.message;

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
    public static class ResultParams extends Message.Params {
        private String barcode;
        private int errorCode;
        private int errorReason;
    }
}
