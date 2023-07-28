package org.opentcs.kernel.vehicles.rms.message;

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
    static class CommandParams extends Message.Params {
        private List<TargetID> targetID;

        private int toteZ;

        @JsonProperty("toteDircetion")
        private int toteDirection;

        private int bufferZ;

        private String barcode;
        /**
         * 充电选项, <code>1</code>-充电; <code>2</code>-打断充电
         */
        private short command;

        @Data
        static class TargetID {
            private int location;
            private double theta;
        }
    }
}
