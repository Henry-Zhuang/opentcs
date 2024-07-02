package org.opentcs.common.rms.robot;

public enum MTDStatus {
  IDLE(1),  // 空闲
  MOVING(2),  // 底盘移动中
  OPERATING(3),  // 执行机构工作中
  ABRUPT_STOP(4),  // 急停
  ERROR(5),  // 故障
  RESTORE(6),  // 待回原或回原中
  MANUAL_CONTROL(7);  // 手动控制中

  private final Integer value;

  MTDStatus(int value){
    this.value = value;
  }

  public int getValue() {
    return this.value;
  }
}
