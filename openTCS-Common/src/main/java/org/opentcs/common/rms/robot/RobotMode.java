package org.opentcs.common.rms.robot;

public enum RobotMode {
  AUTO(1),  // 自动模式
  MANUAL(2);  // 手动模式

  private final Integer value;

  RobotMode(int value){
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }
}
