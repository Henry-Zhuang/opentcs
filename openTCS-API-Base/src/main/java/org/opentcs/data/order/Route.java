/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.data.order;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import static java.util.Objects.requireNonNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opentcs.data.model.Path;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
import static org.opentcs.util.Assertions.checkArgument;

/**
 * A route for a {@link Vehicle}, consisting of a sequence of steps (pairs of {@link Path}s and
 * {@link Point}s) that need to be processed in their given order.
 */
public class Route
    implements Serializable {

  /**
   * The sequence of steps this route consists of, in the order they are to be processed.
   */
  private final List<Step> steps;
  /**
   * The costs for travelling this route.
   */
  private final long costs;

  /**
   * Creates a new Route.
   *
   * @param routeSteps The sequence of steps this route consists of.
   * @param routeCosts The costs for travelling this route.
   */
  public Route(@Nonnull List<Step> routeSteps, long routeCosts) {
    requireNonNull(routeSteps, "routeSteps");
    checkArgument(!routeSteps.isEmpty(), "routeSteps may not be empty");
    steps = Collections.unmodifiableList(new ArrayList<>(routeSteps));
    costs = routeCosts;
  }

  /**
   * Returns the sequence of steps this route consists of.
   *
   * @return The sequence of steps this route consists of.
   * May be empty.
   * The returned <code>List</code> is unmodifiable.
   */
  @Nonnull
  public List<Step> getSteps() {
    return steps;
  }

  /**
   * Returns the costs for travelling this route.
   *
   * @return The costs for travelling this route.
   */
  public long getCosts() {
    return costs;
  }

  /**
   * Returns the final destination point that is reached by travelling this route.
   * (I.e. returns the destination point of this route's last step.)
   *
   * @return The final destination point that is reached by travelling this route.
   */
  @Nonnull
  public Point getFinalDestinationPoint() {
    return steps.get(steps.size() - 1).getDestinationPoint();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Route)) {
      return false;
    }
    final Route other = (Route) o;
    return costs == other.costs
        && Objects.equals(steps, other.steps);
  }

  @Override
  public int hashCode() {
    return Objects.hash(steps, costs);
  }

  @Override
  public String toString() {
    return "Route{" + "steps=" + steps + ", costs=" + costs + '}';
  }

  /**
   * A single step in a route.
   */
  public static class Step
      implements Serializable {

    /**
     * The path to travel.
     */
    private final Path path;
    /**
     * The point that the vehicle is starting from.
     */
    private final Point sourcePoint;
    /**
     * The point that is reached by travelling the path.
     */
    private final Point destinationPoint;
    /**
     * The direction into which the vehicle is supposed to travel.
     */
    private final Vehicle.Orientation vehicleOrientation;
    /**
     * 执行此路段的车辆的车头朝向角度（角度制）.
     * 根据起点、终点坐标以及是否反走来计算车头朝向；如果起始点为空，则车头朝向角度为NaN.
     */
    private final double vehicleDirection;
    /**
     * This step's index in the vehicle's route.
     */
    private final int routeIndex;
    /**
     * Whether execution of this step is allowed.
     */
    private final boolean executionAllowed;
    /**
     * Marks this {@link Step} as the origin of a recalculated route and indicates which
     * {@link ReroutingType} was used to determine the (new) route.
     * <p>
     * Might be {@code null}, if this {@link Step} is not the origin of a recalculated route.
     */
    private final ReroutingType reroutingType;

    /**
     * Creates a new instance.
     *
     * @param path The path to travel.
     * @param srcPoint The point that the vehicle is starting from.
     * @param destPoint The point that is reached by travelling the path.
     * @param orientation The vehicle's orientation on this step.
     * @param routeIndex This step's index in the vehicle's route.
     * @param executionAllowed Whether execution of this step is allowed.
     * @param reroutingType Marks this step as the origin of a recalculated route.
     */
    public Step(@Nullable Path path,
                @Nullable Point srcPoint,
                @Nonnull Point destPoint,
                @Nonnull Vehicle.Orientation orientation,
                int routeIndex,
                boolean executionAllowed,
                @Nullable ReroutingType reroutingType) {
      this.path = path;
      this.sourcePoint = srcPoint;
      this.destinationPoint = requireNonNull(destPoint, "destPoint");
      this.vehicleOrientation = requireNonNull(orientation, "orientation");
      this.routeIndex = routeIndex;
      this.executionAllowed = executionAllowed;
      this.reroutingType = reroutingType;
      this.vehicleDirection = (srcPoint == null)? Double.NaN : calculateVehicleDirection(srcPoint, destPoint, orientation);
    }

    /**
     * Creates a new instance.
     *
     * @param path The path to travel.
     * @param srcPoint The point that the vehicle is starting from.
     * @param destPoint The point that is reached by travelling the path.
     * @param orientation The vehicle's orientation on this step.
     * @param routeIndex This step's index in the vehicle's route.
     * @param executionAllowed Whether execution of this step is allowed.
     */
    public Step(@Nullable Path path,
                @Nullable Point srcPoint,
                @Nonnull Point destPoint,
                @Nonnull Vehicle.Orientation orientation,
                int routeIndex,
                boolean executionAllowed) {
      this(path, srcPoint, destPoint, orientation, routeIndex, executionAllowed, null);
    }

    /**
     * Creates a new instance.
     *
     * @param path The path to travel.
     * @param srcPoint The point that the vehicle is starting from.
     * @param destPoint The point that is reached by travelling the path.
     * @param orientation The vehicle's orientation on this step.
     * @param routeIndex This step's index in the vehicle's route.
     */
    public Step(@Nullable Path path,
                @Nullable Point srcPoint,
                @Nonnull Point destPoint,
                @Nonnull Vehicle.Orientation orientation,
                int routeIndex) {
      this(path, srcPoint, destPoint, orientation, routeIndex, true, null);
    }

    /**
     * Returns the path to travel.
     *
     * @return The path to travel. May be <code>null</code> if the vehicle does
     * not really have to move.
     */
    @Nullable
    public Path getPath() {
      return path;
    }

    /**
     * Returns the point that the vehicle is starting from.
     *
     * @return The point that the vehicle is starting from.
     * May be <code>null</code> if the vehicle does not really have to move.
     */
    @Nullable
    public Point getSourcePoint() {
      return sourcePoint;
    }

    /**
     * Returns the point that is reached by travelling the path.
     *
     * @return The point that is reached by travelling the path.
     */
    @Nonnull
    public Point getDestinationPoint() {
      return destinationPoint;
    }

    /**
     * Returns the direction into which the vehicle is supposed to travel.
     *
     * @return The direction into which the vehicle is supposed to travel.
     */
    @Nonnull
    public Vehicle.Orientation getVehicleOrientation() {
      return vehicleOrientation;
    }

    /**
     * 返回执行此路段的车辆的车头朝向角度（角度制）.
     * 根据起点、终点坐标以及是否反走来计算车头朝向；如果起始点为空，则车头朝向角度为NaN.
     *
     * @return 执行此路段的车辆的车头朝向角度（角度制）.
     */
    public double getVehicleDirection() {
      return vehicleDirection;
    }

    /**
     * Returns this step's index in the vehicle's route.
     *
     * @return This step's index in the vehicle's route.
     */
    public int getRouteIndex() {
      return routeIndex;
    }

    /**
     * Returns whether execution of this step is allowed.
     *
     * @return {@code true}, if execution of this step is allowed, otherwise {@code false}.
     */
    public boolean isExecutionAllowed() {
      return executionAllowed;
    }

    /**
     * Returns the {@link ReroutingType} of this step.
     * <p>
     * Idicates whether this step is the origin of a recalculated route, and if so, which
     * {@link ReroutingType} was used to determine the (new) route.
     * <p>
     * Might return {@code null}, if this step is not the origin of a recalculated route.
     *
     * @return The {@link ReroutingType} of this step.
     */
    @Nullable
    public ReroutingType getReroutingType() {
      return reroutingType;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Step)) {
        return false;
      }
      Step other = (Step) o;
      return Objects.equals(path, other.path)
          && Objects.equals(sourcePoint, other.sourcePoint)
          && Objects.equals(destinationPoint, other.destinationPoint)
          && Objects.equals(vehicleOrientation, other.vehicleOrientation)
          && routeIndex == other.routeIndex
          && executionAllowed == other.executionAllowed
          && reroutingType == other.reroutingType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(path, sourcePoint, destinationPoint, vehicleOrientation, routeIndex,
                          executionAllowed, reroutingType);
    }

    @Override
    public String toString() {
      return destinationPoint.getName();
    }

    /**
     * 计算两点间的路段要求的车辆方向角
     *
     * @param sourcePoint 起始
     * @param destPoint 目标
     * @param orientation 路段方向，正走或倒走
     * @return 车辆方向角，范围[-180°, 180°)
     */
    public static double calculateVehicleDirection(Point sourcePoint, Point destPoint, Vehicle.Orientation orientation) {
      // 根据起始点与终点的坐标计算车头朝向角度（角度制）
      double angle = Math.toDegrees(Math.atan2(
          destPoint.getPosition().getY() - sourcePoint.getPosition().getY(),
          destPoint.getPosition().getX() - sourcePoint.getPosition().getX()
      ));

      //根据车头朝向角度计算车头方向
      if (orientation == Vehicle.Orientation.BACKWARD)
        angle += 180;  // 反向取反

      // 将角度范围限制在[-180, 180)
      if (angle >= 180)
        angle -= 360;
      return angle;
    }
  }
}
