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
      if (toteZ < 50)
        checkArgument(stack_layers >= toteZ, "toteZ must be <= stack_layers for pick command(if toteZ < 50)");
      // 放空校验
      checkArgument(buffer_layers + 1 >= bufferZ, "bufferZ must be <= buffer_layers + 1 for pick command");
      if (bufferZ == MTD_MAX){
        checkArgument(toteZ != 1, "1 to N invalid command");
        checkArgument(stack_layers == toteZ, "no obstacles allowed above toteZ when bufferZ == MAX_LAYER");
      } else if (bufferZ == 1) {
        checkArgument(toteZ != MTD_MAX, "N to 1 invalid command");
        checkArgument(
            buffer_layers + 1 == bufferZ || stack_layers == toteZ,
            "no obstacles allowed above toteZ when bufferZ == 1 and there are obstacles above bufferZ"
        );
      }
      if (toteZ == MTD_MAX){
        checkArgument(buffer_layers + 1 == bufferZ, "bufferZ must be the top of the buffer when toteZ == MAX_LAYER");
      } else if (toteZ == 1) {
        checkArgument(
            buffer_layers + 1 == bufferZ || stack_layers == toteZ,
            "bufferZ must be the top of the buffer when toteZ == 1 and there are obstacles above toteZ");
      }
    } else if (type.equals(Command.Type.PLACE.getType())){  // 放箱指令校验
      // 抓空校验
      checkArgument(buffer_layers >= bufferZ, "bufferZ must be <= buffer_layers for place command");
      // 放空校验
      checkArgument(stack_layers + 1 >= toteZ, "toteZ must be <= stack_layers + 1 for place command");
      if (bufferZ == MTD_MAX){
        checkArgument(toteZ != 1, "1 to N invalid command");
        checkArgument(stack_layers + 1 == toteZ, "no obstacles allowed above toteZ when bufferZ == MAX_LAYER");
      } else if (bufferZ == 1) {
        checkArgument(toteZ != MTD_MAX, "N to 1 invalid command");
        checkArgument(
            buffer_layers == bufferZ || stack_layers + 1 == toteZ,
            "no obstacles allowed above toteZ when bufferZ == 1 and there are obstacles above bufferZ"
        );
      }
      if (toteZ == MTD_MAX){
        checkArgument(buffer_layers == bufferZ, "no obstacles allowed above bufferZ when toteZ == MAX_LAYER");
      } else if (toteZ == 1) {
        checkArgument(
            buffer_layers == bufferZ || stack_layers + 1 == toteZ,
            "no obstacles allowed above bufferZ when toteZ == 1 and there are obstacles above toteZ"
        );
      }
    }
  }
}
