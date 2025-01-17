package org.opentcs.common.rms.message;

import com.google.common.primitives.UnsignedLong;
import org.opentcs.common.rms.NameConvertor;
import org.opentcs.common.rms.robot.MTDStatus;
import org.opentcs.common.rms.robot.RobotMode;
import org.opentcs.common.rms.robot.RobotType;
import org.opentcs.drivers.vehicle.VehicleProcessModel;

import lombok.NonNull;

import java.util.ArrayList;
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
                                            Boolean isLaneY,
                                            Boolean isPaused) {
    Heartbeat.HeartbeatParams params = new Heartbeat.HeartbeatParams();

    params.setRobotID(NameConvertor.toRobotId(vehicleModel.getName()));
    params.setUniqueID(vehicleModel.getUniqueId());
    params.setMode(RobotMode.AUTO.getValue());

    ArrayList<Integer> errors = new ArrayList<>();
    int status = MTDStatus.IDLE.getValue();
    if (vehicleModel.isMoving())
      status = MTDStatus.MOVING.getValue();
    if (vehicleModel.isOperating())
      status = MTDStatus.OPERATING.getValue();
    if (vehicleModel.getVehicleState().isAbruptStop()) {
      status = MTDStatus.ABRUPT_STOP.getValue();
      errors.add(23);
    } else if (vehicleModel.getVehicleState().isUnavailable()){
      status = MTDStatus.ERROR.getValue();
      errors.add(21);
    } else if (vehicleModel.getVehicleState().isUnknown()) {
      status = MTDStatus.ERROR.getValue();
      errors.add(20);
    } else if (vehicleModel.getVehicleState().isError()) {
      status = MTDStatus.ERROR.getValue();
      errors.add(12);
    }
    params.setStatus(status);

    if (isPaused)
      errors.add(22);
    params.setErrors(errors);
    params.setPosition(NameConvertor.toPointId(vehicleModel.getVehiclePosition()));
    double theta = vehicleModel.getVehicleOrientationAngle();
    if (!Double.isNaN(theta)) {
      if (isLaneY)
        theta = theta - 90;
      theta = Math.toRadians(theta);
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
