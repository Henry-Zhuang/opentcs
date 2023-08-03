package org.opentcs.virtualvehicle.rms.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Heartbeat extends Message {
    private HeartbeatParams params;

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HeartbeatParams extends Params {
        private Integer status;
        private Integer position;
        private Double theta;
        private BatteryInfo batteryInfo;
        private Double odo;
        private Double today_odo;

        public HeartbeatParams() {
        }

        public HeartbeatParams(int status,
                               int position,
                               double theta,
                               BatteryInfo batteryInfo,
                               double odo,
                               double today_odo) {
            this.status = status;
            this.position = position;
            this.theta = theta;
            this.batteryInfo = batteryInfo;
            this.odo = odo;
            this.today_odo = today_odo;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class BatteryInfo {
            private Double voltage;
            private Double current;
            private Double capacityRemain;
            private Boolean chargingStatus;
            private Boolean chargerConnected;
            private Double temperature;
            private Double percentage;

            public BatteryInfo() {
            }

            public BatteryInfo(double voltage,
                               double current,
                               double capacityRemain,
                               boolean chargingStatus,
                               boolean chargerConnected,
                               double temperature,
                               double percentage) {
                this.voltage = voltage;
                this.current = current;
                this.capacityRemain = capacityRemain;
                this.chargingStatus = chargingStatus;
                this.chargerConnected = chargerConnected;
                this.temperature = temperature;
                this.percentage = percentage;
            }
        }
    }
}
