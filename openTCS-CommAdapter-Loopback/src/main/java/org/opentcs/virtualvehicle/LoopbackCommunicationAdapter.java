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
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
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
    ((ExecutorService) getExecutor()).submit(() -> getProcessModel().setVehiclePosition(newPos));
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
              step.getVehicleOrientation())
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

    WayEntry prevWayEntry = getProcessModel().getVelocityController().getCurrentWayEntry();
    getProcessModel().getVelocityController().advanceTime(getSimulationTimeStep());
    energyConsumeSimulation(getSimulationTimeStep());  // 模拟电量消耗
    WayEntry currentWayEntry = getProcessModel().getVelocityController().getCurrentWayEntry();
    //if we are still on the same way entry then reschedule to do it again
    if (prevWayEntry == currentWayEntry) {
      ((ScheduledExecutorService) getExecutor()).schedule(() -> movementSimulation(command),
          SIMULATION_TASKS_DELAY,
          TimeUnit.MILLISECONDS);
    } else {
      //if the way enties are different then we have finished this step
      //and we can move on.
      getProcessModel().setVehicleOrientationAngle(
          calculateAngle(getProcessModel().getVehiclePosition(), prevWayEntry.getDestPointName())
      );
      getProcessModel().setVehiclePosition(prevWayEntry.getDestPointName());
      LOG.debug("Movement simulation finished.");
      if (!command.isWithoutOperation()) {
        LOG.debug("Starting operation simulation...");
        ((ScheduledExecutorService) getExecutor()).schedule(() -> operationSimulation(command),
            SIMULATION_TASKS_DELAY,
            TimeUnit.MILLISECONDS);
      } else {
        finishVehicleSimulation(command);
      }
    }
  }

  private double calculateAngle(String srcPointName, String destPointName) {
    // 若目标点有指定Angle, 则使用该Angle
    Point destPoint = requireNonNull(vehicleService.fetchObject(Point.class, destPointName));
    if (!Double.isNaN(destPoint.getVehicleOrientationAngle()))
      return destPoint.getVehicleOrientationAngle();
    // 若起始点有指定Angle, 则使用该Angle
    Point srcPoint = requireNonNull(vehicleService.fetchObject(Point.class, srcPointName));
    if (!Double.isNaN(srcPoint.getVehicleOrientationAngle()))
      return srcPoint.getVehicleOrientationAngle();
    // 若两点均无指定Angle, 则根据两点坐标计算Angle
    return Math.toDegrees(Math.atan2(
        destPoint.getPosition().getY() - srcPoint.getPosition().getY(),
        destPoint.getPosition().getX() - srcPoint.getPosition().getX()
    ));
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
    ((ScheduledExecutorService) getExecutor()).schedule(() -> energyGrowSimulation(getSimulationTimeStep()),
        SIMULATION_TASKS_DELAY,
        TimeUnit.MILLISECONDS);
    finishVehicleSimulation(command);
  }

  private void stopRechargeSimulation(MovementCommand command) {
    getProcessModel().setChargerConnected(false);
    finishVehicleSimulation(command);
  }

  private void jointOperationSimulation(MovementCommand command) {
    operationSimulationTimePassed += getSimulationTimeStep();

    if (operationSimulationTimePassed < JOINT_OPERATION_TIME) {
      getProcessModel().getVelocityController().advanceTime(getSimulationTimeStep());
      energyConsumeSimulation(getSimulationTimeStep());  // 模拟电量消耗
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
      finishVehicleSimulation(command);
    }
  }

  private void loadUnloadOperationSimulation(MovementCommand command) {
    operationSimulationTimePassed += getSimulationTimeStep();

    if (operationSimulationTimePassed < getProcessModel().getOperatingTime()) {
      getProcessModel().getVelocityController().advanceTime(getSimulationTimeStep());
      energyConsumeSimulation(getSimulationTimeStep());  // 模拟电量消耗
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
      finishVehicleSimulation(command);
    }
  }

  private void energyGrowSimulation(double chargingTime) {
    if (!getProcessModel().isChargerConnected())
      return;

    double oriEnergyLevel = getProcessModel().getVehicleEnergyLevel();
    if (oriEnergyLevel < 100) {
      double increment = (chargingTime / getProcessModel().getFullRechargingTime()) * 100;
      double curEnergyLevel = Math.min(oriEnergyLevel + increment, 100.0);
      getProcessModel().setVehicleEnergyLevel(curEnergyLevel);
      ((ScheduledExecutorService) getExecutor()).schedule(() -> energyGrowSimulation(getSimulationTimeStep()),
          SIMULATION_TASKS_DELAY,
          TimeUnit.MILLISECONDS);
    } else {
      getProcessModel().setChargerConnected(false);
      getProcessModel().setVehicleState(Vehicle.State.IDLE);
    }
  }

  private void energyConsumeSimulation(int runningTime) {
    double oriEnergyLevel = getProcessModel().getVehicleEnergyLevel();
    if (oriEnergyLevel <= 0.0)
      return;
    double decrement = (((double) runningTime) / getProcessModel().getFullRunningTime()) * 100;
    double curEnergyLevel = Math.max(oriEnergyLevel - decrement, 0.0);
    getProcessModel().setVehicleEnergyLevel(curEnergyLevel);
  }

  private void finishVehicleSimulation(MovementCommand command) {
    //Set the vehicle state to idle
    if (getSentQueue().size() <= 1
        && getCommandQueue().isEmpty()
        && !command.getOperation().equals(getRechargeOperation())
    ) {
      getProcessModel().setVehicleState(Vehicle.State.IDLE);
      getProcessModel().setMoving(false);
    }
    if (Objects.equals(getSentQueue().peek(), command)) {
      // Let the comm adapter know we have finished this command.
      getProcessModel().commandExecuted(getSentQueue().poll());
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
