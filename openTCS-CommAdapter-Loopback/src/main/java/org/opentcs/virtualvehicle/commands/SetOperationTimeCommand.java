/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle.commands;

import org.opentcs.drivers.vehicle.AdapterCommand;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.virtualvehicle.LoopbackCommunicationAdapter;

/**
 * A command to set a vehicle's maximum reverse velocity.
 */
public class SetOperationTimeCommand
    implements AdapterCommand {

  /**
   * The operation time to set.
   */
  private final int opTime;

  /**
   * Creates a new instance.
   *
   * @param opTime The operation time to set.
   */
  public SetOperationTimeCommand(int opTime) {
    this.opTime = opTime;
  }

  @Override
  public void execute(VehicleCommAdapter adapter) {
    ((LoopbackCommunicationAdapter) adapter).getProcessModel().setOperatingTime(opTime);
  }
}
