package org.opentcs.virtualvehicle.rms;

import org.opentcs.common.rms.message.Command;

public class RmsTest {
  public static void main(String[] args) {
    String type = "move";
    System.out.println(Command.Type.fromString(type));
  }
}
