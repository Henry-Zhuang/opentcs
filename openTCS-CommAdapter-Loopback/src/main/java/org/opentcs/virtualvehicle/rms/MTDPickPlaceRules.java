package org.opentcs.virtualvehicle.rms;

import org.opentcs.common.rms.message.Command;

import static org.opentcs.util.Assertions.checkArgument;

public class MTDPickPlaceRules {

  private static final Integer MTD_MAX = 10;

  public static void validate(Command cmd){
    int bufferZ = cmd.getParams().getBufferZ();
    int toteZ = cmd.getParams().getToteZ();
    int buffer_layers = cmd.getParams().getBufferLayers();
    int stack_layers = cmd.getParams().getStackLayers();
    String type = cmd.getType();
    if (type.equals(Command.Type.PICK.getType())){  // 取箱指令校验
      // 抓空校验
      checkArgument(stack_layers >= toteZ, "取箱指令取堆塔的空位置");
      // 放空校验
      checkArgument(buffer_layers + 1 >= bufferZ, "取箱指令取至背篓悬空位置");
      if (bufferZ == MTD_MAX){
        checkArgument(toteZ != 1, "1-N非法指令");
        checkArgument(stack_layers == toteZ, "取至背篓第N层时，待取料箱上方不可有障碍箱");
      } else if (bufferZ == 1) {
        checkArgument(toteZ != MTD_MAX, "N-1非法指令");
        checkArgument(
            buffer_layers + 1 == bufferZ || stack_layers == toteZ,
            "取至背篓第1层时，若背篓1层有障碍箱，则待取料箱上方不可有障碍箱"
        );
      }
      if (toteZ == MTD_MAX){
        checkArgument(buffer_layers + 1 == bufferZ, "取堆塔第N层时，只能放到背篓最上层");
      } else if (toteZ == 1) {
        checkArgument(
            buffer_layers + 1 == bufferZ || stack_layers == toteZ,
            "取堆塔第1层时，若待取料箱上方有障碍箱，则只能放在背篓最上层");
      }
    } else if (type.equals(Command.Type.PLACE.getType())){  // 放箱指令校验
      // 抓空校验
      checkArgument(buffer_layers >= bufferZ, "放箱指令取背篓的空位置");
      // 放空校验
      checkArgument(stack_layers + 1 >= toteZ, "放箱指令放至堆塔悬空位置");
      if (bufferZ == MTD_MAX){
        checkArgument(toteZ != 1, "1-N非法指令");
        checkArgument(stack_layers + 1 == toteZ, "将背篓第N层料箱放至堆塔时，只能放到堆塔最上层");
      } else if (bufferZ == 1) {
        checkArgument(toteZ != MTD_MAX, "N-1非法指令");
        checkArgument(
            buffer_layers == bufferZ || stack_layers + 1 == toteZ,
            "将背篓第1层料箱放至堆塔时，若背篓第1层上方有障碍箱，则只能放到堆塔最上层"
        );
      }
      if (toteZ == MTD_MAX){
        checkArgument(buffer_layers == bufferZ, "放至堆塔第N层时，待取背篓料箱上方不可有障碍箱");
      } else if (toteZ == 1) {
        checkArgument(
            buffer_layers == bufferZ || stack_layers + 1 == toteZ,
            "放至堆塔第1层时，若堆塔1层有障碍箱，则背篓待取料箱上方不可有障碍箱"
        );
      }
    }
  }
}
