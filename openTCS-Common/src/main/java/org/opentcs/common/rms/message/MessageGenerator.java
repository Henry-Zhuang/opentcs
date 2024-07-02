package org.opentcs.common.rms.message;

import com.google.common.primitives.UnsignedLong;
import org.opentcs.common.rms.NameConvertor;
import org.opentcs.common.rms.robot.MTBStatus;
import org.opentcs.common.rms.robot.MTDStatus;
import org.opentcs.common.rms.robot.RobotType;
import org.opentcs.drivers.vehicle.VehicleProcessModel;

import lombok.NonNull;

import java.util.Objects;
import java.util.UUID;

public class MessageGenerator {
  public static void packMsg(@NonNull Message msg,
                             @NonNull Command.Type type,
                             @NonNull Integer robotTypeValue,
                             boolean needAck) {
    // 添加消息公共部分
    msg.setDeviceType(robotTypeValue);
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


  public static Heartbeat generateHeartbeat(@NonNull VehicleProcessModel vehicleModel,
                                            @NonNull RobotType robotType,
                                            Boolean isMoving) {
    Heartbeat.HeartbeatParams params = new Heartbeat.HeartbeatParams();

    params.setRobotID(NameConvertor.toRobotId(vehicleModel.getName()));
    params.setUniqueID(vehicleModel.getUniqueId());
    int status;
    if (robotType.equals(RobotType.MT_D)){
      status = isMoving ? MTDStatus.MOVING.getValue(): MTDStatus.IDLE.getValue();
    } else {
      status = isMoving ? MTBStatus.MOVING.getValue() : MTBStatus.IDLE.getValue();
    }
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
    packMsg(hb, Command.Type.HEARTBEAT, robotType.getValue(), false);
    return hb;
  }

  public static Response generateAck(@NonNull Message msg, @NonNull RobotType robotType) {
    Message.Params params = new Message.Params();
    params.setRobotID(msg.getParams().getRobotID());
    params.setUniqueID(msg.getParams().getUniqueID());
    Response ack = new Response();
    ack.setParams(params);
    Command.Type type = Command.Type.fromString(Objects.requireNonNull(msg.getType(), "type"));
    packMsg(ack, type, robotType.getValue(), false);
    return ack;
  }

  public static Result generateResult(@NonNull Integer robotID,
                                      UnsignedLong uniqueID,
                                      @NonNull Command.Type type,
                                      @NonNull Integer errorCode,
                                      @NonNull Integer errorReason,
                                      String actualBarcode,
                                      @NonNull RobotType robotType,
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
    packMsg(result, type, robotType.getValue(), needAck);
    return result;
  }
}
