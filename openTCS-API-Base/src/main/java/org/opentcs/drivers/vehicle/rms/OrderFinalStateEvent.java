package org.opentcs.drivers.vehicle.rms;

import org.opentcs.data.order.TransportOrder;

import java.io.Serializable;

public class OrderFinalStateEvent
    implements Serializable {
  private final String orderName;
  private final String orderType;
  private final String vehicleName;

  private final TransportOrder.State finalState;

  public OrderFinalStateEvent(String orderName,
                              String orderType,
                              String vehicleName,
                              TransportOrder.State finalState) {
    this.orderName = orderName;
    this.orderType = orderType;
    this.vehicleName = vehicleName;
    this.finalState = finalState;
  }

  public String getOrderName() {
    return orderName;
  }

  public String getOrderType() {
    return orderType;
  }

  public String getVehicleName() {
    return vehicleName;
  }

  public TransportOrder.State getFinalState() {
    return finalState;
  }

  @Override
  public String toString() {
    return "OrderStateEvent{"
        + "orderName=" + orderName
        + ", vehicleName=" + vehicleName
        + ", finalState=" + finalState
        + '}';
  }
}
