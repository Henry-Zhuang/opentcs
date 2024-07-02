package org.opentcs.common.rms.robot;

public enum MTBStatus {
  IDLE(0),  // 空闲
  MOVING(1),  // 底盘移动中
  ABRUPT_STOP(3),  // 急停
  RESTORE(21);  // 待回原或回原中

  private final Integer value;

  MTBStatus(int value){
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }
}
