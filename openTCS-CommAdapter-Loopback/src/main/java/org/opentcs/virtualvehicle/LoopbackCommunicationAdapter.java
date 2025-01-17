/**
 * Copyright (c) The openTCS Authors.
 * <p>
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.virtualvehicle;

import com.google.inject.assistedinject.Assisted;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
//import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.opentcs.common.LoopbackAdapterConstants;
import org.opentcs.common.rms.SocketConstants;
import org.opentcs.common.rms.message.Command;
import org.opentcs.components.kernel.services.DispatcherService;
import org.opentcs.components.kernel.services.InternalTransportOrderService;
import org.opentcs.components.kernel.services.InternalVehicleService;
import org.opentcs.customizations.ApplicationEventBus;
import org.opentcs.customizations.kernel.KernelExecutor;
import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.model.Path;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.Route;
import org.opentcs.data.order.Route.Step;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.drivers.vehicle.BasicVehicleCommAdapter;
import org.opentcs.drivers.vehicle.LoadHandlingDevice;
import org.opentcs.drivers.vehicle.MovementCommand;
import org.opentcs.drivers.vehicle.SimVehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleCommAdapter;
import org.opentcs.drivers.vehicle.VehicleProcessModel;
import org.opentcs.drivers.vehicle.management.VehicleProcessModelTO;
import org.opentcs.util.ExplainedBoolean;
import org.opentcs.util.event.EventSource;
import org.opentcs.virtualvehicle.VelocityController.WayEntry;
import org.opentcs.virtualvehicle.rms.SocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link VehicleCommAdapter} that does not really communicate with a physical vehicle but roughly
 * simulates one.
 */
public class LoopbackCommunicationAdapter
    extends BasicVehicleCommAdapter
    implements SimVehicleCommAdapter {

  /**
   * The name of the load handling device set by this adapter.
   */
  public static final String LHD_NAME = "default";
  /**
   * This class's Logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(LoopbackCommunicationAdapter.class);
  /**
   * An error code indicating that there's a conflict between a load operation and the vehicle's
   * current load state.
   */
  private static final String LOAD_OPERATION_CONFLICT = "cannotLoadWhenLoaded";
  /**
   * An error code indicating that there's a conflict between an unload operation and the vehicle's
   * current load state.
   */
  private static final String UNLOAD_OPERATION_CONFLICT = "cannotUnloadWhenNotLoaded";
  /**
   * The time by which to advance the velocity controller per step (in ms).
   */
  private static final int ADVANCE_TIME = 100;
  /**
   * The delay to use for scheduling the various simulation tasks (in ms).
   */
  private static final int SIMULATION_TASKS_DELAY = 100;
  /**
   * The time needed for executing joint operation.
   */
  private static final int JOINT_OPERATION_TIME = 3000;
  /**
   * This instance's configuration.
   */
  private final VirtualVehicleConfiguration configuration;
  /**
   * Indicates whether the vehicle simulation is running or not.
   */
  private volatile boolean isSimulationRunning;
  /**
   * The vehicle to this comm adapter instance.
   */
  private final Vehicle vehicle;

  private final SocketClient socketClient;
  private final InternalVehicleService vehicleService;
  /**
   * The vehicle's load state.
   */
  private LoadState loadState = LoadState.EMPTY;
  /**
   * The number of containers loaded by the vehicle.
   */
  private Integer loadedContainerNum = 0;
  /**
   * Whether the loopback adapter is initialized or not.
   */
  private boolean initialized;
  /**
   * The amount of time that passed during the simulation of an operation.
   */
  private int operationSimulationTimePassed;

  /**
   * Creates a new instance.
   *
   * @param configuration  This class's configuration.
   * @param vehicle        The vehicle this adapter is associated with.
   * @param kernelExecutor The kernel's executor.
   */
  @Inject
  public LoopbackCommunicationAdapter(VirtualVehicleConfiguration configuration,
                                      @Assisted Vehicle vehicle,
                                      @KernelExecutor ScheduledExecutorService kernelExecutor,
                                      @ApplicationEventBus EventSource eventSource,
                                      @Nonnull InternalTransportOrderService orderService,
                                      @Nonnull DispatcherService dispatcherService,
                                      @Nonnull InternalVehicleService vehicleService) {
    super(new LoopbackVehicleModel(
            vehicle,
            configuration.defaultAcceleration(),
            configuration.defaultDeceleration(),
            configuration.defaultOperatingTime(),
            configuration.defaultRechargingTime()),
        configuration.commandQueueCapacity(),
        1,
        LoopbackAdapterConstants.PROPVAL_RECHARGE_OPERATION_DEFAULT,
        LoopbackAdapterConstants.PROPVAL_STOP_RECHARGE_OPERATION_DEFAULT,
        kernelExecutor);
    this.vehicle = requireNonNull(vehicle, "vehicle");
    this.configuration = requireNonNull(configuration, "configuration");
    String serverIp = vehicle.getProperty(SocketConstants.PROPERTY_KEY_SERVER_IP);
    String serverPort = vehicle.getProperty(SocketConstants.PROPERTY_KEY_SERVER_PORT);
    this.socketClient = new SocketClient(
        getProcessModel(),
        eventSource,
        orderService,
        dispatcherService,
        vehicleService,
        serverIp != null ? serverIp : configuration.socketServerIp(),
        serverPort != null ? serverPort : configuration.socketServerPort(),
        configuration.isLaneY()
    );
    this.vehicleService = requireNonNull(vehicleService);
  }

  @Override
  public void initialize() {
    if (isInitialized()) {
      return;
    }
    super.initialize();

    String initialPos
        = vehicle.getProperties().get(LoopbackAdapterConstants.PROPKEY_INITIAL_POSITION);
    if (initialPos != null) {
      initVehiclePosition(initialPos);
    }
    getProcessModel().setVehicleState(Vehicle.State.IDLE);
    getProcessModel().setVehicleLoadHandlingDevices(
        Arrays.asList(new LoadHandlingDevice(LHD_NAME, false))
    );
    socketClient.initialize();
    initialized = true;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void terminate() {
    if (!isInitialized()) {
      return;
    }

    socketClient.terminate();
    super.terminate();
    initialized = false;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    super.propertyChange(evt);

    if (!((evt.getSource()) instanceof LoopbackVehicleModel)) {
      return;
    }
    if (Objects.equals(evt.getPropertyName(),
        VehicleProcessModel.Attribute.LOAD_HANDLING_DEVICES.name())) {
      if (!getProcessModel().getVehicleLoadHandlingDevices().isEmpty()
          && getProcessModel().getVehicleLoadHandlingDevices().get(0).isFull()) {
        loadState = LoadState.FULL;
        getProcessModel().setVehicleLength(configuration.vehicleLengthLoaded());
      } else {
        loadState = LoadState.EMPTY;
        getProcessModel().setVehicleLength(configuration.vehicleLengthUnloaded());
      }
    }
  }

  @Override
  public synchronized void enable() {
    if (isEnabled()) {
      return;
    }
    getProcessModel().getVelocityController().addVelocityListener(getProcessModel());
    super.enable();
    socketClient.enable();
    if (getProcessModel().getVehicleEnergyLevel() == 0) {
      getProcessModel().setVehicleEnergyLevel(100);
    }
    energyChangeSimulation();
  }

  @Override
  public synchronized void disable() {
    if (!isEnabled()) {
      return;
    }
    getProcessModel().getVelocityController().removeVelocityListener(getProcessModel());
    socketClient.disable();
    super.disable();
  }

  @Override
  public LoopbackVehicleModel getProcessModel() {
    return (LoopbackVehicleModel) super.getProcessModel();
  }

  @Override
  public synchronized void sendCommand(MovementCommand cmd) {
    requireNonNull(cmd, "cmd");

    // Start the simulation task is the single step modus is not active.
    if (!getProcessModel().isSingleStepModeEnabled()) {
      isSimulationRunning = true;
      ((ExecutorService) getExecutor()).submit(() -> startVehicleSimulation(cmd));
    }
  }

  @Override
  public void onVehiclePaused(boolean paused) {
    getProcessModel().setVehiclePaused(paused);
  }

  @Override
  public void processMessage(Object message) {
  }

  @Override
  public synchronized void initVehiclePosition(String newPos) {
    ((ExecutorService) getExecutor()).submit(() -> setVehiclePositionAndDirection(newPos));
  }

  @Override
  public synchronized ExplainedBoolean canProcess(TransportOrder order) {
    requireNonNull(order, "order");

    return canProcess(
        order.getFutureDriveOrders().stream()
            .map(driveOrder -> driveOrder.getDestination().getOperation())
            .collect(Collectors.toList())
    );
  }

  @Override
  @Deprecated
  public synchronized ExplainedBoolean canProcess(List<String> operations) {
    requireNonNull(operations, "operations");

    LOG.debug("{}: Checking processability of {}...", getName(), operations);
    boolean canProcess = true;
    String reason = "";

    // Do NOT require the vehicle to be IDLE or CHARGING here!
    // That would mean a vehicle moving to a parking position or recharging location would always
    // have to finish that order first, which would render a transport order's dispensable flag
    // useless.
//    Integer currLoadedNum = loadedContainerNum;
//    Iterator<String> opIter = operations.iterator();
//    while (canProcess && opIter.hasNext()) {
//      final String nextOp = opIter.next();
//      // 取箱则装载数加一，放箱则装载数减一；若装载数量变为负数，则说明不可执行，若装载数量超出机器人背篓容量，也说明不可执行.
//      // 目前尚未增加机器人背篓容量的相关配置，暂时默认机器人背篓容量无上限；
//      if (nextOp.startsWith(getProcessModel().getLoadOperation())) {
//        currLoadedNum += 1;
//      } else if (nextOp.startsWith(getProcessModel().getUnloadOperation())) {
//        currLoadedNum -= 1;
//      }
//      if (currLoadedNum < 0) {
//        canProcess = false;
//        reason = UNLOAD_OPERATION_CONFLICT;
//      }
//    }
    if (!canProcess) {
      LOG.debug("{}: Cannot process {}, reason: '{}'", getName(), operations, reason);
    }
    return new ExplainedBoolean(canProcess, reason);
  }

  @Override
  protected synchronized void connectVehicle() {
  }

  @Override
  protected synchronized void disconnectVehicle() {
  }

  @Override
  protected synchronized boolean isVehicleConnected() {
    return true;
  }

  @Override
  protected VehicleProcessModelTO createCustomTransferableProcessModel() {
    return new LoopbackVehicleModelTO()
        .setLoadOperation(getProcessModel().getLoadOperation())
        .setMaxAcceleration(getProcessModel().getMaxAcceleration())
        .setMaxDeceleration(getProcessModel().getMaxDecceleration())
        .setMaxFwdVelocity(getProcessModel().getMaxFwdVelocity())
        .setMaxRevVelocity(getProcessModel().getMaxRevVelocity())
        .setOperatingTime(getProcessModel().getOperatingTime())
        .setSingleStepModeEnabled(getProcessModel().isSingleStepModeEnabled())
        .setUnloadOperation(getProcessModel().getUnloadOperation())
        .setVehiclePaused(getProcessModel().isVehiclePaused());
  }

  /**
   * Triggers a step in single step mode.
   */
  public synchronized void trigger() {
    if (getProcessModel().isSingleStepModeEnabled()
        && !getSentQueue().isEmpty()
        && !isSimulationRunning) {
      isSimulationRunning = true;
      ((ExecutorService) getExecutor()).submit(() -> startVehicleSimulation(getSentQueue().peek()));
    }
  }

  private void energyChangeSimulation() {
    if (!isEnabled()) {
      return;
    }

    // 模拟电量变化
    double factor = getProcessModel().getEnergyChangeFactor();
    double delta = (getSimulationTimeStep() / getProcessModel().getFullRunningTime()) * 100 * factor;
    double newLevel = getProcessModel().getVehicleEnergyLevel() + delta;
    if (newLevel < 0) {
      // 电量耗尽，车辆进入关机状态
      getProcessModel().setVehicleEnergyLevel(0);
      disable();
      return;
    } else if (newLevel > 100) {
      // 电量充满，车辆自动停止充电
      getProcessModel().setVehicleEnergyLevel(100);
      getProcessModel().setVehicleState(Vehicle.State.IDLE);
    } else {
      // 电量变化
      getProcessModel().setVehicleEnergyLevel(newLevel);
    }
    ((ScheduledExecutorService) getExecutor()).schedule(this::energyChangeSimulation,
        SIMULATION_TASKS_DELAY,
        TimeUnit.MILLISECONDS);
  }

  private void setVehiclePositionAndDirection(String newPos) {
    Point point = null;
    try {
      point = vehicleService.fetchObject(Point.class, newPos);
    }
    catch (Exception ex) {
      LOG.warn("Error fetching point", ex);
    }
    if (point != null) {
      getProcessModel().setVehiclePosition(newPos);
      getProcessModel().setVehicleOrientationAngle(getVehicleDirectionByPath(point));
    }
  }

  private double getVehicleDirectionByPath(Point point) {
    Point otherPoint = null;
    Vehicle.Orientation forwardOrBackward = Vehicle.Orientation.FORWARD;
    double direction = Double.NaN;

    // 从出度路径中获取车辆的方向角
    if (!point.getOutgoingPaths().isEmpty()) {
      TCSObjectReference<Path> outRef = point.getOutgoingPaths().iterator().next();
      Path outPath = null;
      try {
        outPath = vehicleService.fetchObject(Path.class, outRef);
      }
      catch (Exception ex) {
        LOG.warn("Error fetching outPath", ex);
      }
      if (outPath != null) {
        // 查询出度路径的目的点
        TCSObjectReference<Point> pointRef = outPath.getDestinationPoint();
        try {
          otherPoint = vehicleService.fetchObject(Point.class, pointRef);
        }
        catch (Exception ex) {
          LOG.warn("Error fetching destination point", ex);
        }
      }
    }

    // 从入度路径中获取车辆的方向角
    if (otherPoint == null && !point.getIncomingPaths().isEmpty()) {
      // 查询入度路径
      TCSObjectReference<Path> inRef = point.getIncomingPaths().iterator().next();
      Path inPath = null;
      try {
        inPath = vehicleService.fetchObject(Path.class, inRef);
      }
      catch (Exception ex) {
        LOG.warn("Error fetching inPath", ex);
      }
      if (inPath != null) {
        // 查询入度路径的起始点
        TCSObjectReference<Point> srcRef = inPath.getSourcePoint();
        try {
          otherPoint = vehicleService.fetchObject(Point.class, srcRef);
        }
        catch (Exception ex) {
          LOG.warn("Error fetching source point", ex);
        }
        forwardOrBackward = Vehicle.Orientation.BACKWARD;
      }
    }

    if (otherPoint != null) {
      direction = Route.Step.calculateVehicleDirection(point, otherPoint, forwardOrBackward);
    }
    return direction;
  }

  private void startVehicleSimulation(MovementCommand command) {
    LOG.debug("Starting vehicle simulation for command: {}", command);
    Step step = command.getStep();
    getProcessModel().setVehicleState(Vehicle.State.EXECUTING);
    operationSimulationTimePassed = 0;

    if (step.getPath() == null) {
      LOG.debug("Starting operation simulation...");
      ((ScheduledExecutorService) getExecutor()).schedule(() -> operationSimulation(command),
          SIMULATION_TASKS_DELAY,
          TimeUnit.MILLISECONDS);
    } else {
      getProcessModel().getVelocityController().addWayEntry(
          new WayEntry(step.getPath().getLength(),
              maxVelocity(step),
              step.getDestinationPoint().getName(),
              step.getVehicleOrientation(),
              step.getVehicleDirection())
      );
      getProcessModel().setMoving(true);
      LOG.debug("Starting movement simulation...");
      ((ScheduledExecutorService) getExecutor()).schedule(() -> movementSimulation(command),
          SIMULATION_TASKS_DELAY,
          TimeUnit.MILLISECONDS);
    }
  }

  private int maxVelocity(Step step) {
    return (step.getVehicleOrientation() == Vehicle.Orientation.BACKWARD)
        ? step.getPath().getMaxReverseVelocity()
        : step.getPath().getMaxVelocity();
  }

  private void movementSimulation(MovementCommand command) {
    if (!getProcessModel().getVelocityController().hasWayEntries()) {
      return;
    }

    // 若车辆处于非正常状态，则停止模拟，将当前指令置为失败
    if (getProcessModel().getVehicleState().isUnhealthy()) {
      LOG.debug("{}: Vehicle is in {} state, stopping movement simulation.", getName(), getProcessModel().getVehicleState());
      getProcessModel().getVelocityController().finishCurWayEntry();
      finishVehicleSimulation(command, false);
      return;
    }

    // 若车辆处于暂停状态，则暂停模拟
    if (getProcessModel().isVehiclePaused()) {
      LOG.debug("{}: Vehicle is paused, pausing movement simulation.", getName());
      getProcessModel().setMoving(false);
      ((ScheduledExecutorService) getExecutor()).schedule(() -> movementSimulation(command),
          SIMULATION_TASKS_DELAY,
          TimeUnit.MILLISECONDS);
      return;
    }

    // 模拟车辆移动
    WayEntry prevWayEntry = getProcessModel().getVelocityController().getCurrentWayEntry();
    // 移动前判断车辆朝向是否符合路径要求的车辆方向角度一致
    double sourceDir = getProcessModel().getVehicleOrientationAngle();
    double requiredDir = getWayDirection(prevWayEntry, getProcessModel().getVehiclePosition());
    if (calculateAngleDiff(sourceDir, requiredDir)  != 0) {
      // 若车辆朝向不符合要求，则模拟车辆转向
      rotationSimulation(command, sourceDir, requiredDir);
    } else {
      // 若车辆朝向符合要求，则模拟车辆前进
      getProcessModel().getVelocityController().advanceTime(getSimulationTimeStep());
      WayEntry currentWayEntry = getProcessModel().getVelocityController().getCurrentWayEntry();
      //if we are still on the same way entry then reschedule to do it again
      if (prevWayEntry == currentWayEntry) {
        // 若车辆未走完当前路段，则继续模拟车辆前进
        ((ScheduledExecutorService) getExecutor()).schedule(() -> movementSimulation(command),
            SIMULATION_TASKS_DELAY,
            TimeUnit.MILLISECONDS);
      } else {
        //if the way enties are different then we have finished this step
        //and we can move on.
        // 若车辆已走完当前路段，则变更车辆位置点，并执行该路段的后续动作（若有指定动作）
        getProcessModel().setVehiclePosition(prevWayEntry.getDestPointName());
        LOG.debug("Movement simulation finished.");
        if (!command.isWithoutOperation()) {
          // 若该路段有后续动作，则开始执行后续动作
          LOG.debug("Starting operation simulation...");
          ((ScheduledExecutorService) getExecutor()).schedule(() -> operationSimulation(command),
              SIMULATION_TASKS_DELAY,
              TimeUnit.MILLISECONDS);
        } else {
          // 若该路段无后续动作，则完成该指令的模拟
          finishVehicleSimulation(command, true);
        }
      }
    }
  }

  private double getWayDirection(WayEntry wayEntry, String vehiclePosition) {
    double direction = wayEntry.getVehicleDirection();
    if (Double.isNaN(direction)) {
      Point srcPoint = requireNonNull(vehicleService.fetchObject(Point.class, vehiclePosition));
      Point destPoint = requireNonNull(vehicleService.fetchObject(Point.class, wayEntry.getDestPointName()));
      direction = Step.calculateVehicleDirection(srcPoint, destPoint, wayEntry.getVehicleOrientation());
    }
    return direction;
  }

  private double calculateAngleDiff(double sourceDir, double targetDir) {
    // 计算从sourceDir到targetDir的最小夹角，若为正，表示逆时针旋转，若为负，表示顺时针旋转
    double diff = (targetDir - sourceDir + 360) % 360;
    if (diff > 180) {
      diff -= 360;
    }
    return diff;
  }

  private void rotationSimulation(MovementCommand command, double sourceDir, double requiredDir) {
    // FIXME: 这里暂时简化车辆以最大角速度旋转，后续可考虑加入转向时的加速度、减速度等因素
    double angularVelocity = getProcessModel().getMaxAngularVelocity();
    // 计算最小夹角，来决定旋转方向
    double diff = calculateAngleDiff(sourceDir, requiredDir);
    double delta = Math.min(Math.abs(diff), angularVelocity * getSimulationTimeStep() / 1000);
    double newDir;
    if (diff > 0) {
      // 逆时针旋转
      newDir = sourceDir + delta;
      if (newDir >= 180)
        newDir -= 360;
    } else {
      // 顺时针旋转
      newDir = sourceDir - delta;
      if (newDir < -180)
        newDir += 360;
    }
    // 修改车辆方向角度
    getProcessModel().setVehicleOrientationAngle(newDir);
    // 一旦旋转，则说明车辆移动速度降为0
    getProcessModel().getVelocityController().setCurrentVelocity(0);

    ((ScheduledExecutorService) getExecutor()).schedule(() -> movementSimulation(command),
        SIMULATION_TASKS_DELAY,
        TimeUnit.MILLISECONDS);
  }

  private void operationSimulation(MovementCommand command) {
    LOG.debug("operation: {}", command.getOperation());
    if (command.getOperation().equals(getRechargeOperation()))
      rechargeSimulation(command);
    else if (command.getOperation().equals(getStopRechargeOperation()))
      stopRechargeSimulation(command);
    else if (command.getOperation().equals(Command.Type.JOINT.getType())
        || command.getOperation().equals(Command.Type.JOINT_B.getType()))
      jointOperationSimulation(command);
    else
      loadUnloadOperationSimulation(command);
  }

  private void rechargeSimulation(MovementCommand command) {
    getProcessModel().setVehicleState(Vehicle.State.CHARGING);
    getProcessModel().setChargerConnected(true);
    finishVehicleSimulation(command, true);
  }

  private void stopRechargeSimulation(MovementCommand command) {
    getProcessModel().setChargerConnected(false);
    finishVehicleSimulation(command, true);
  }

  private void jointOperationSimulation(MovementCommand command) {
    operationSimulationTimePassed += getSimulationTimeStep();

    if (operationSimulationTimePassed < JOINT_OPERATION_TIME) {
      getProcessModel().getVelocityController().advanceTime(getSimulationTimeStep());
      ((ScheduledExecutorService) getExecutor()).schedule(() -> operationSimulation(command),
          SIMULATION_TASKS_DELAY,
          TimeUnit.MILLISECONDS);
    } else {
      LOG.debug("Joint operation simulation finished.");
//      if (loadedContainerNum > 0) {
//        loadedContainerNum = 0;
//        getProcessModel().setVehicleLoadHandlingDevices(
//            Arrays.asList(new LoadHandlingDevice(LHD_NAME, false))
//        );
//      } else {
//        loadedContainerNum = 8;
//        getProcessModel().setVehicleLoadHandlingDevices(
//            Arrays.asList(new LoadHandlingDevice(LHD_NAME, true))
//        );
//      }
      finishVehicleSimulation(command, true);
    }
  }

  private void loadUnloadOperationSimulation(MovementCommand command) {
    operationSimulationTimePassed += getSimulationTimeStep();

    if (operationSimulationTimePassed < getProcessModel().getOperatingTime()) {
      getProcessModel().setOperating(true);
      getProcessModel().getVelocityController().advanceTime(getSimulationTimeStep());
      ((ScheduledExecutorService) getExecutor()).schedule(() -> operationSimulation(command),
          SIMULATION_TASKS_DELAY,
          TimeUnit.MILLISECONDS);
    } else {
      LOG.debug("Operation simulation finished.");
//      String operation = command.getOperation();
//      if (operation.equals(getProcessModel().getLoadOperation())) {
//        // 增加装载的料箱数量
//        loadedContainerNum += 1;
//        if (loadedContainerNum == 1)
//          // Update load handling devices as defined by this operation
//          getProcessModel().setVehicleLoadHandlingDevices(
//              Arrays.asList(new LoadHandlingDevice(LHD_NAME, true))
//          );
//      } else if (operation.equals(getProcessModel().getUnloadOperation())) {
//        // 减少装载的料箱数量
//        loadedContainerNum -= 1;
//        if (loadedContainerNum == 0)
//          getProcessModel().setVehicleLoadHandlingDevices(
//              Arrays.asList(new LoadHandlingDevice(LHD_NAME, false))
//          );
//      }
      getProcessModel().setOperating(false);
      finishVehicleSimulation(command, true);
    }
  }

  private void finishVehicleSimulation(MovementCommand command, boolean success) {
    //Set the vehicle state to idle
    if (getSentQueue().size() <= 1
        && getCommandQueue().isEmpty()
        && !command.getOperation().equals(getRechargeOperation())
    ) {
      getProcessModel().setMoving(false);
      if (!getProcessModel().getVehicleState().isUnhealthy())
        getProcessModel().setVehicleState(Vehicle.State.IDLE);
    }
    if (Objects.equals(getSentQueue().peek(), command)) {
      // Let the comm adapter know we have finished this command.
      if (success) {
        getProcessModel().commandExecuted(getSentQueue().poll());
      } else {
        LOG.warn("{}: Simulated command failed: {}", getName(), command);
        getProcessModel().setMoving(false);
        getProcessModel().setOperating(false);
        getProcessModel().commandFailed(getSentQueue().peek());
      }
    } else {
      LOG.warn("{}: Simulated command not oldest in sent queue: {} != {}",
          getName(),
          command,
          getSentQueue().peek());
    }
    isSimulationRunning = false;
  }

  private int getSimulationTimeStep() {
    return (int) (ADVANCE_TIME * configuration.simulationTimeFactor());
  }

  /**
   * The vehicle's possible load states.
   */
  private enum LoadState {
    EMPTY,
    FULL;
  }
}
