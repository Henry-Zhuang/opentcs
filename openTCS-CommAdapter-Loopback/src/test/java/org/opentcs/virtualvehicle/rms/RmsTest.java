package org.opentcs.virtualvehicle.rms;

import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;

import java.util.Arrays;
import java.util.UUID;

public class RmsTest {
  public static void main(String[] args) {
    String objectName = "Point-4012";
    Integer objectId = ObjectNameConvertor.toObjectId(Point.class, objectName);
    System.out.println(objectId);
    System.out.println(ObjectNameConvertor.toObjectName(Vehicle.class, 5));
  }
}
