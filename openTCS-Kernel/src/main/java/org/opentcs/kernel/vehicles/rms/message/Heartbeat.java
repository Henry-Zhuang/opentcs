package org.opentcs.kernel.vehicles.rms.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Heartbeat extends Message {
    private HeartbeatParams params;

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class HeartbeatParams extends Message.Params {
        private int status;
        private int position;
        private double theta;
        private BatteryInfo batteryInfo;
        private double odo;
        private double today_odo;

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
        public static class BatteryInfo {
            private double voltage;
            private double current;
            private double capacityRemain;
            private boolean chargingStatus;
            private boolean chargerConnected;
            private double temperature;
            private double percentage;

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
