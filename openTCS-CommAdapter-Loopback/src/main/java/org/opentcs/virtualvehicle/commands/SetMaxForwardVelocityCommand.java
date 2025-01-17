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
 * A command to set a vehicle's maximum forward velocity.
 */
public class SetMaxForwardVelocityCommand
    implements AdapterCommand {

  /**
   * The maximum forward velocity to set.
   */
  private final int velocity;

  /**
   * Creates a new instance.
   *
   * @param velocity The maximum forward velocity to set.
   */
  public SetMaxForwardVelocityCommand(int velocity) {
    this.velocity = velocity;
  }

  @Override
  public void execute(VehicleCommAdapter adapter) {
    ((LoopbackCommunicationAdapter) adapter).getProcessModel().setMaxFwdVelocity(velocity);
  }
}
