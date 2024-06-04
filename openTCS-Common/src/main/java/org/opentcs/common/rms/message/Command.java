package org.opentcs.common.rms.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
  public static class CommandParams extends Params {
    private List<TargetID> targetID;

    private Integer toteZ;

    @JsonProperty("toteDircetion")
    private Integer toteDirection;

    private Integer bufferZ;

    private String barcode;
    /**
     * 取放箱指令参数, 待操作堆塔的当前总层数
     */
    private Integer stackLayers;
    /**
     * 取放箱指令参数, 机器人背篓的当前总层数
     */
    private Integer bufferLayers;
    /**
     * 取放箱指令参数, 待操作库位上层料箱的总重量
     */
    private Double upperWeight;
    /**
     * 充电选项, <code>1</code>-充电; <code>2</code>-打断充电
     */
    private Short command;
    /**
     * Z机器人对接选项, <code>1</code>-取箱对接; <code>2</code>-放箱对接
     */
    private Short action_type;

    @Data
    public static class TargetID {
      private Integer location;
      private Double theta;
    }
  }

  public enum Type {
    PICK("pick", Channel.CONTROL.ordinal()),
    PLACE("place", Channel.CONTROL.ordinal()),
    MOVE("move", Channel.CONTROL.ordinal()),
    JOINT("joint", Channel.CONTROL.ordinal()),
    JOINT_B("joint_b", Channel.CONTROL.ordinal()),
    CANCEL("cancel", Channel.CONTROL.ordinal()),
    PAUSE("pause", Channel.CONTROL.ordinal()),
    CONTINUE("continue", Channel.CONTROL.ordinal()),
    CHARGE("charge", Channel.CONTROL.ordinal()),
    LIFT("lift", Channel.CONTROL.ordinal()),
    STOP_CHARGE("stop charge", Channel.CONTROL.ordinal()),
    OFFLINE("offline", Channel.MANAGEMENT.ordinal()),
    ONLINE("online", Channel.MANAGEMENT.ordinal()),
    SHUTDOWN("shutdown", Channel.MANAGEMENT.ordinal()),
    TURN_OFF_ALARM("turn_off_alarm", Channel.MANAGEMENT.ordinal()),

    HEARTBEAT("heartbeat", Channel.DATA.ordinal()),
    STATISTICS("statistics", Channel.DATA.ordinal());

    private final String type;
    private final int channel;

    Type(String type, int channel) {
      this.type = type;
      this.channel = channel;
    }

    public String getType() {
      return type;
    }

    public int getChannel() {
      return channel;
    }

    public static Type fromString(String type) {
      if (type != null) {
        for(Type t : Type.values()){
          if (type.equalsIgnoreCase(t.type))
            return t;
        }
      }
      return null;
    }
  }
}
