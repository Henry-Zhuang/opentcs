package org.opentcs.common.rms.message;

import com.google.common.primitives.UnsignedLong;
import org.opentcs.common.rms.NameConvertor;
import org.opentcs.drivers.vehicle.VehicleProcessModel;

import lombok.NonNull;

import java.util.Objects;
import java.util.UUID;

public class MessageGenerator {
  public static void packMsg(@NonNull Message msg, @NonNull Command.Type type, boolean needAck) {
    // 添加消息公共部分
    msg.setDeviceType(1);
    msg.setChannel(type.getChannel());
    if (type.equals(Command.Type.STOP_CHARGE))
      msg.setType(Command.Type.CHARGE.getType());
    else
      msg.setType(type.getType());
    // 添加消息头部
    final Message.Header header = new Message.Header();
    header.setMsgSeq(UUID.randomUUID().toString().replace("-", ""));
    header.setMsgMode(needAck ? Message.Header.Mode.ACK.getValue() : Message.Header.Mode.NO_ACK.getValue());
    msg.setHeader(header);
  }


  public static Heartbeat generateHeartbeat(@NonNull VehicleProcessModel vehicleModel, Boolean isMoving) {
    Heartbeat.HeartbeatParams params = new Heartbeat.HeartbeatParams();

    params.setRobotID(NameConvertor.toRobotId(vehicleModel.getName()));
    params.setUniqueID(vehicleModel.getUniqueId());
    Integer status = isMoving ? 1 : 0;
    params.setStatus(status);
    params.setPosition(NameConvertor.toPointId(vehicleModel.getVehiclePosition()));
    double theta = vehicleModel.getVehicleOrientationAngle();
    if (!Double.isNaN(theta)) {
      theta = Math.toRadians(theta - 90);
      params.setTheta(theta);
    }
    params.setOdo(10.0);
    params.setToday_odo(20.0);
    Heartbeat.HeartbeatParams.BatteryInfo batteryInfo = new Heartbeat.HeartbeatParams.BatteryInfo();
    batteryInfo.setPercentage(vehicleModel.getVehicleEnergyLevel());
    batteryInfo.setChargerConnected(vehicleModel.isChargerConnected());
    batteryInfo.setChargingStatus(vehicleModel.isChargerConnected());
    params.setBatteryInfo(batteryInfo);

    Heartbeat hb = new Heartbeat();
    hb.setParams(params);
    packMsg(hb, Command.Type.HEARTBEAT, false);
    return hb;
  }

  public static Response generateAck(@NonNull Message msg) {
    Message.Params params = new Message.Params();
    params.setRobotID(msg.getParams().getRobotID());
    params.setUniqueID(msg.getParams().getUniqueID());
    Response ack = new Response();
    ack.setParams(params);
    Command.Type type = Command.Type.fromString(Objects.requireNonNull(msg.getType(), "type"));
    packMsg(ack, type, false);
    return ack;
  }

  public static Result generateResult(@NonNull Integer robotID,
                                      UnsignedLong uniqueID,
                                      @NonNull Command.Type type,
                                      @NonNull Integer errorCode,
                                      @NonNull Integer errorReason,
                                      String actualBarcode,
                                      boolean needAck) {
    Result.ResultParams params = new Result.ResultParams();
    params.setRobotID(robotID);
    params.setUniqueID(uniqueID);
    params.setErrorCode(errorCode);
    params.setErrorReason(errorReason);
    if (actualBarcode != null)
      params.setBarcode(actualBarcode);

    Result result = new Result();
    result.setParams(params);
    packMsg(result, type, needAck);
    return result;
  }
}
