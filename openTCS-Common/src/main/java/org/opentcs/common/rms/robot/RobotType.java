package org.opentcs.common.rms.robot;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum RobotType {
  MT_B(1),
  MT_Z(2),
  MT_D(3);

  private final int value;

  RobotType(int value){
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }

  public static Set<String> names() {
    return Arrays.stream(RobotType.values()).map(RobotType::name).collect(Collectors.toSet());
  }

  public static RobotType findByName(String name) {
    if (name == null)
      return null;
    RobotType result = null;
    for (RobotType type: RobotType.values()){
      if (type.name().equalsIgnoreCase(name)) {
        result = type;
        break;
      }
    }
    return result;
  }
}
