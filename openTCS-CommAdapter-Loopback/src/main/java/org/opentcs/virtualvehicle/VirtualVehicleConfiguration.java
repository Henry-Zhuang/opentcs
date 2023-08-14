/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

import org.opentcs.configuration.ConfigurationEntry;
import org.opentcs.configuration.ConfigurationPrefix;

/**
 * Provides methods to configure to {@link LoopbackCommunicationAdapter}.
 */
@ConfigurationPrefix(VirtualVehicleConfiguration.PREFIX)
public interface VirtualVehicleConfiguration {

  /**
   * This configuration's prefix.
   */
  String PREFIX = "virtualvehicle";

  @ConfigurationEntry(
      type = "Boolean",
      description = "Whether to enable to register/enable the loopback driver.",
      orderKey = "0_enable")
  boolean enable();

  @ConfigurationEntry(
      type = "Integer",
      description = "The adapter's command queue capacity.",
      orderKey = "1_attributes_1")
  int commandQueueCapacity();

  @ConfigurationEntry(
      type = "Integer",
      description = "The virtual vehicle's default maximum acceleration (in mm/s).",
      orderKey = "1_attributes_4")
  int defaultAcceleration();

  @ConfigurationEntry(
      type = "Integer",
      description = "The virtual vehicle's default maximum deceleration (in mm/s).",
      orderKey = "1_attributes_5")
  int defaultDeceleration();

  @ConfigurationEntry(
      type = "Double",
      description = {"The simulation time factor.",
                     "1.0 is real time, greater values speed up simulation."},
      orderKey = "2_behaviour_1")
  double simulationTimeFactor();

  @ConfigurationEntry(
      type = "Integer",
      description = {"The virtual vehicle's length in mm when it's loaded."},
      orderKey = "2_behaviour_2")
  int vehicleLengthLoaded();

  @ConfigurationEntry(
      type = "Integer",
      description = {"The virtual vehicle's length in mm when it's unloaded."},
      orderKey = "2_behaviour_3")
  int vehicleLengthUnloaded();

  @ConfigurationEntry(
      type = "Double",
      description = {"The default time needed for vehicle recharging from empty energy to full energy."},
      orderKey = "2_behaviour_4")
  double defaultRechargingTime();

  @ConfigurationEntry(
      type = "Double",
      description = {"The default time needed for vehicle loading or unloading."},
      orderKey = "2_behaviour_5")
  int defaultOperatingTime();

  @ConfigurationEntry(
      type = "String",
      description = {"The socket server ip address that the virtual vehicle will connect to."},
      orderKey = "3_socket_1")
  String socketServerIp();

  @ConfigurationEntry(
      type = "String",
      description = {"The socket server port that the virtual vehicle will connect to."},
      orderKey = "3_socket_2")
  String socketServerPort();
}
