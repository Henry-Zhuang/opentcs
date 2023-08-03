package org.opentcs.virtualvehicle.rms.message;

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
}
