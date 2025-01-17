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
 * A command to set a vehicle's maximum deceleration.
 */
public class SetMaxDecelerationCommand
    implements AdapterCommand {

  /**
   * The maximum deceleration to set.
   */
  private final int deceleration;

  /**
   * Creates a new instance.
   *
   * @param deceleration The maximum deceleration to set.
   */
  public SetMaxDecelerationCommand(int deceleration) {
    this.deceleration = deceleration;
  }

  @Override
  public void execute(VehicleCommAdapter adapter) {
    ((LoopbackCommunicationAdapter) adapter).getProcessModel().setMaxDeceleration(deceleration);
  }
}
