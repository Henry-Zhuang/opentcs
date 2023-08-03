package org.opentcs.virtualvehicle.rms.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Command extends Message {
    private CommandParams params;

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    static class CommandParams extends Params {
        private List<TargetID> targetID;

        private Integer toteZ;

        @JsonProperty("toteDircetion")
        private Integer toteDirection;

        private Integer bufferZ;

        private String barcode;
        /**
         * 充电选项, <code>1</code>-充电; <code>2</code>-打断充电
         */
        private Short command;

        @Data
        static class TargetID {
            private Integer location;
            private Double theta;
        }
    }
}
