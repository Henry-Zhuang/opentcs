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
 * A command to set a vehicle's maximum acceleration.
 */
public class SetMaxAccelerationCommand
    implements AdapterCommand {

  /**
   * The maximum acceleration to set.
   */
  private final int acceleration;

  /**
   * Creates a new instance.
   *
   * @param acceleration The maximum acceleration to set.
   */
  public SetMaxAccelerationCommand(int acceleration) {
    this.acceleration = acceleration;
  }

  @Override
  public void execute(VehicleCommAdapter adapter) {
    ((LoopbackCommunicationAdapter) adapter).getProcessModel().setMaxAcceleration(acceleration);
  }
}
