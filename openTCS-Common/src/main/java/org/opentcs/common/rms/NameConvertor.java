package org.opentcs.common.rms;

import com.google.common.primitives.UnsignedLong;
import lombok.NonNull;
import org.opentcs.data.model.Point;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameConvertor {
  private static final String NAME_FORMAT = "%s-%s";
  private static final String NAME_REGEXP_FORMAT = "%s-([0-9]+)";
  private static final String COMMAND_PREFIX = "Command";
  private static final String ROBOT_PREFIX = "Robot";
  private static final String POINT_PREFIX = Point.class.getSimpleName();
  public static final String RECHARGE_PREFIX = "Recharge";
  private static final String STACK_NAME_FORMAT = "%s-%s-%s";
  private static final String STACK_PREFIX = "Stack";
  private static final String STATION_PREFIX = "Station";

  private static String toObjectName(@NonNull String objectPrefix, @NonNull String objectId) {
    return String.format(NAME_FORMAT, objectPrefix, objectId);
  }

  private static Integer toObjectId(@NonNull String objectPrefix, String objectName) {
    if (objectName != null) {
      String regexp = String.format(NAME_REGEXP_FORMAT, objectPrefix);
      Pattern pattern = Pattern.compile(regexp);
      Matcher matcher = pattern.matcher(objectName);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
    }
    return null;
  }

  public static String toCommandName(@NonNull UnsignedLong uniqueId) {
    return String.format(NAME_FORMAT, COMMAND_PREFIX, uniqueId);
  }

  public static UnsignedLong toCommandId(String orderName) {
    if (orderName != null) {
      String regexp = String.format(NAME_REGEXP_FORMAT, COMMAND_PREFIX);
      Pattern pattern = Pattern.compile(regexp);
      Matcher matcher = pattern.matcher(orderName);
      if (matcher.find()) {
        return UnsignedLong.valueOf(matcher.group(1));
      }
    }
    return null;
  }

  public static String toRobotName(int robotId) {
    return toObjectName(ROBOT_PREFIX, String.valueOf(robotId));
  }

  public static Integer toRobotId(String robotName) {
    return toObjectId(ROBOT_PREFIX, robotName);
  }

  public static String toPointName(@NonNull Integer pointId) {
    return toObjectName(POINT_PREFIX, pointId.toString());
  }

  public static Integer toPointId(String objectName) {
    return toObjectId(POINT_PREFIX, objectName);
  }

  public static String toRechargeName(@NonNull Integer rechargeId) {
    return toObjectName(RECHARGE_PREFIX, String.valueOf(rechargeId));
  }

  public static String toStackName(@NonNull Integer stackId, @NonNull Integer toteDirection) {
    String direction = toteDirection == 0 ? "L" : "R";
    return String.format(STACK_NAME_FORMAT, STACK_PREFIX, stackId, direction);
  }

  public static String toStationName(@NonNull Integer stationId) {
    return toObjectName(STATION_PREFIX, String.valueOf(stationId));
  }
}
