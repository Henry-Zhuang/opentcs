/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.common;

import org.opentcs.common.rms.message.Command;

/**
 * This interface provides access to vehicle-property keys that are used in both the
 * plant overview and in the kernel.
 */
public interface LoopbackAdapterConstants {

  /**
   * The key of the vehicle property that specifies the vehicle's initial position.
   */
  String PROPKEY_INITIAL_POSITION = "loopback:initialPosition";
  /**
   * The key of the vehicle property that specifies the default operating time.
   */
  String PROPKEY_OPERATING_TIME = "loopback:operatingTime";
  /**
   * The key of the vehicle property that specifies the full recharging time.
   */
  String PROPKEY_FULL_RECHARGING_TIME = "loopback:fullRechargingTime";
  /**
   * The key of the vehicle property that specifies which operation loads the load handling device.
   */
  String PROPKEY_LOAD_OPERATION = "loopback:loadOperation";
  /**
   * The default value of the load operation property.
   */
  String PROPVAL_LOAD_OPERATION_DEFAULT = Command.Type.PICK.getType();
  /**
   * The key of the vehicle property that specifies which operation unloads the load handling
   * device.
   */
  String PROPKEY_UNLOAD_OPERATION = "loopback:unloadOperation";
  /**
   * The default value of the unload operation property.
   */
  String PROPVAL_UNLOAD_OPERATION_DEFAULT = Command.Type.PLACE.getType();
  String PROPVAL_JOINT_OPERATION_DEFAULT = Command.Type.JOINT.getType();
  String PROPVAL_RECHARGE_OPERATION_DEFAULT = Command.Type.CHARGE.getType();
  String PROPVAL_STOP_RECHARGE_OPERATION_DEFAULT = Command.Type.STOP_CHARGE.getType();

  /**
   * The key of the vehicle property that specifies the maximum acceleration of a vehicle.
   */
  String PROPKEY_ACCELERATION = "loopback:acceleration";
  /**
   * The key of the vehicle property that specifies the maximum deceleration of a vehicle.
   */
  String PROPKEY_DECELERATION = "loopback:deceleration";

}
