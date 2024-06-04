package org.opentcs.virtualvehicle.rms;

import com.google.common.primitives.UnsignedLong;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.Data;
import lombok.NonNull;
import org.opentcs.access.to.order.DestinationCreationTO;
import org.opentcs.access.to.order.TransportOrderCreationTO;
import org.opentcs.common.rms.NameConvertor;
import org.opentcs.common.rms.SocketConstants;
import org.opentcs.common.rms.message.*;
import org.opentcs.components.Lifecycle;
import org.opentcs.components.kernel.services.DispatcherService;
import org.opentcs.components.kernel.services.InternalTransportOrderService;
import org.opentcs.components.kernel.services.InternalVehicleService;
import org.opentcs.customizations.ApplicationEventBus;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.OrderConstants;
import org.opentcs.data.order.TransportOrder;
import org.opentcs.drivers.vehicle.rms.OrderFinalStateEvent;
import org.opentcs.util.event.EventHandler;
import org.opentcs.util.event.EventSource;
import org.opentcs.virtualvehicle.LoopbackVehicleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.opentcs.data.order.DriveOrder.Destination.OP_NOP;
import static org.opentcs.util.Assertions.checkArgument;
import static org.opentcs.util.Assertions.checkInRange;

public class SocketClient implements EventHandler, Lifecycle {
  /**
   * This class's Logger.
   */
  public static final Logger LOG = LoggerFactory.getLogger(SocketClient.class);
  private final EventSource eventSource;
  private final InternalTransportOrderService orderService;
  private final DispatcherService dispatcherService;
  private final InternalVehicleService vehicleService;
  private final LoopbackVehicleModel vehicleModel;
  private final String serverHost;
  private final int serverPort;
  private ScheduledThreadPoolExecutor scheduledTimer;
  private EventLoopGroup workerGroup;
  private Bootstrap bootstrap;
  private Channel channel;
  private boolean initialized = false;
  private volatile boolean enabled = false;
  protected ConcurrentHashMap<UnsignedLong, MessageWrapper> resendTable;

  public SocketClient(@NonNull LoopbackVehicleModel vehicleModel,
                      @ApplicationEventBus EventSource eventSource,
                      @NonNull InternalTransportOrderService orderService,
                      @NonNull DispatcherService dispatcherService,
                      @NonNull InternalVehicleService vehicleService,
                      String serverHost,
                      String serverPort) {
    this.vehicleModel = vehicleModel;
    this.eventSource = eventSource;
    this.orderService = orderService;
    this.dispatcherService = dispatcherService;
    this.vehicleService = vehicleService;
    this.serverHost = serverHost != null ? serverHost : SocketConstants.DEFAULT_SERVER_IP;
    this.serverPort = serverPort != null ?
        checkInRange(Integer.parseInt(serverPort), 1, 65535, "serverPort")
        : SocketConstants.DEFAULT_SERVER_PORT;
  }

  @Override
  public void initialize() {
    if (isInitialized()) {
      return;
    }

    scheduledTimer = new ScheduledThreadPoolExecutor(
        2, new DefaultThreadFactory(String.format("%s_Timer", getVehicleName()), true)
    );
    workerGroup = new NioEventLoopGroup(
        4, new DefaultThreadFactory(String.format("%s_Socket", getVehicleName()), true)
    );
    bootstrap = new Bootstrap();
    bootstrap.group(workerGroup)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, SocketConstants.CONNECT_TIMEOUT)
        .handler(new SocketClientInitializer(this, scheduledTimer));
    //    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
    LOG.debug("Robot socket initialized: {}", getVehicleName());
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

    scheduledTimer.shutdown();
    workerGroup.shutdownGracefully();
//    Future<?> future = workerGroup.shutdownGracefully();
//    try {
//      future.await(2, TimeUnit.SECONDS);
//    } catch (InterruptedException e) {
//      LOG.error("{}: Terminate exception: ", getVehicleName(), e);
//    }
    LOG.debug("Robot socket terminated: {}", getVehicleName());
    initialized = false;
  }

  public synchronized void enable() {
    if (isEnabled()) {
      return;
    }

    resendTable = new ConcurrentHashMap<UnsignedLong, MessageWrapper>(256);
    eventSource.subscribe(this);
    enabled = true;
    connect();
  }

  public synchronized void disable() {
    if (!isEnabled()) {
      return;
    }
    eventSource.unsubscribe(this);

    enabled = false;
    disconnect();
    resendTable.clear();
    resendTable = null;
  }

  public synchronized boolean isEnabled() {
    return enabled;
  }

  public synchronized void connect() {
    if (!isEnabled()) {
      return;
    }

    final ChannelFuture cf = bootstrap.connect(serverHost, serverPort);
    try {
      cf.await(SocketConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS);
      if (cf.isSuccess()) {
        LOG.info("{}: Connected to RMS tcp server({}:{})", getVehicleName(), serverHost, serverPort);
        channel = cf.channel();
      } else if (isEnabled()) {
        LOG.info("{}: Reconnecting to RMS tcp server({}:{})......", getVehicleName(), serverHost, serverPort);
        workerGroup.schedule(this::connect, getReconnectDelayTime(), TimeUnit.SECONDS);
      }
    } catch (InterruptedException ex) {
      LOG.error("{}: Connect to RMS tcp server exception: ", getVehicleName(), ex);
    }
  }

  public synchronized void disconnect() {
    if (channel == null || !channel.isOpen()) {
      return;
    }

    channel.close();
    LOG.info("{}: Disconnected from RMS tcp server({}:{})", getVehicleName(), serverHost, serverPort);
  }

  public String getVehicleName() {
    return vehicleModel.getName();
  }

  public void scanResendTable() {
    try {
      Iterator<Map.Entry<UnsignedLong, MessageWrapper>> it = resendTable.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<UnsignedLong, MessageWrapper> next = it.next();
        MessageWrapper msgWrapper = next.getValue();
        if (msgWrapper.isSendSuccess() && msgWrapper.isAckSuccess()) {
          it.remove();
        } else {
          long time = msgWrapper.getBeginTimestamp() + msgWrapper.getTimeoutMillis();
          if (time <= System.currentTimeMillis()) {
            msgWrapper.releaseAll();
            it.remove();
            // 消息重发
            send(msgWrapper.getMsg(), false);
            LOG.info("{}: Resend message: {}", getVehicleName(), msgWrapper.getMsg());
          }
        }
      }
    } catch (Throwable e) {
      LOG.error("{}: ScanResendTable exception: ", getVehicleName(), e);
    }
  }

  @Override
  public void onEvent(Object event) {
    if (event instanceof OrderFinalStateEvent
        && getVehicleName().equals(((OrderFinalStateEvent) event).getVehicleName())) {
      OrderFinalStateEvent evt = (OrderFinalStateEvent) event;
      if (evt.getOrderType().equals(OrderConstants.TYPE_NONE))
        return;
      int errorCode = evt.getFinalState().equals(TransportOrder.State.FINISHED) ?
          Result.ErrorCode.SUCCEED.ordinal() : Result.ErrorCode.FAIL.ordinal();
      int errorReason = errorCode == 0 ?
          Result.ErrorReason.NONE.getValue() : Result.ErrorReason.OTHER_REASON.getValue();
      sendResult(
          NameConvertor.toCommandId(evt.getOrderName()),
          Command.Type.fromString(evt.getOrderType()),
          errorCode,
          errorReason,
          null, // TODO pick和place指令需要比较数据库与指令要求的barcode，若不同，则应返回actualBarcode
          false);
    }
  }

  public void processReceivedAck(Message msg) {
    if (msg instanceof Response) {
      Response ack = (Response) msg;
      LOG.info("{}: Ack received: {}", getVehicleName(), ack);
      final UnsignedLong uniqueID = ack.getParams().getUniqueID();
      if (uniqueID != null) {
        final MessageWrapper msgWrapper = resendTable.get(uniqueID);
        if (msgWrapper != null) {
          resendTable.remove(uniqueID);
          msgWrapper.setAckSuccess(true);
          msgWrapper.releaseAck();
        } else {
          LOG.warn(
              "{}: Ack for message[{}] was received but not required",
              getVehicleName(),
              ack.getParams().getUniqueID()
          );
        }
      }
    }
  }

  public void processReceivedCommand(Message msg) {
    if (msg instanceof Command) {
      Command cmd = (Command) msg;
      LOG.info("{}: {} command received: {}", getVehicleName(), cmd.getType(), cmd);
      try {
        switch (cmd.getChannel()) {
          case 0:
            handleControlCommand(cmd);
            break;
          case 1:
            handleManagementCommand(cmd);
            break;
          default:
            LOG.warn("{}: Received invalid channel command: {}", getVehicleName(), cmd);
        }
      } catch (Throwable e) {
        LOG.error("{}: {} command exception: ", getVehicleName(), cmd.getType(), e);
        sendResult(
            cmd.getParams().getUniqueID(),
            Command.Type.fromString(cmd.getType()),
            Result.ErrorCode.FAIL.ordinal(),
            Result.ErrorReason.OTHER_REASON.getValue(),
            null,
            false);
      }
    }
  }

  private void handleControlCommand(Command cmd) {
    // 参数检验
    requireNonNull(cmd.getParams().getUniqueID(), "uniqueId");
    // 执行指令
    switch (Command.Type.fromString(requireNonNull(cmd.getType(), "cmdType"))) {
      case PICK:
      case PLACE:
      case JOINT:
      case JOINT_B:
        executePickPlaceJoint(cmd);
        break;
      case MOVE:
        executeMove(cmd);
        break;
      case CHARGE:
        executeCharge(cmd);
        break;
      case CANCEL:
      case PAUSE:
      case CONTINUE:
      case LIFT:
        executeCancelPauseContinueLift(cmd);
        break;
      default:
        LOG.warn("{}: Received unknown type command: {}", getVehicleName(), cmd);
    }
  }

  private void executePickPlaceJoint(Command cmd) {
    // 参数校验
    List<Command.CommandParams.TargetID> targetIds
        = requireNonNull(cmd.getParams().getTargetID(), "targetIds");
    checkArgument(targetIds.size() == 1, "pick/place/joint targetIds size must be 1");
    List<String> jointTypes = Arrays.asList(Command.Type.JOINT.getType(), Command.Type.JOINT_B.getType());
    // 创建指令记录，并执行指令
    String destName = jointTypes.contains(cmd.getType()) ?
        NameConvertor.toStationName(targetIds.get(0).getLocation())
        : NameConvertor.toStackName(targetIds.get(0).getLocation(), cmd.getParams().getToteDirection());
    List<DestinationCreationTO> destinations = List.of(new DestinationCreationTO(destName, cmd.getType()));
    TransportOrderCreationTO orderTO
        = new TransportOrderCreationTO(NameConvertor.toCommandName(cmd.getParams().getUniqueID()), destinations)
        .withIntendedVehicleName(getVehicleName())
        .withType(cmd.getType());
    orderService.createTransportOrder(orderTO);
    dispatcherService.dispatch();
  }

  private void executeMove(Command cmd) {
    // 参数校验
    List<Command.CommandParams.TargetID> targetIds
        = requireNonNull(cmd.getParams().getTargetID(), "targetIds");
    checkArgument(targetIds.size() == 2, "move targetIds size should be 2");

    // 创建指令记录，并执行指令
//    List<DestinationCreationTO> destinations = new ArrayList<>(targetIds.size());
//    targetIds.forEach(targetID -> destinations.add(
//        new DestinationCreationTO(
//            NameConvertor.toPointName(targetID.getLocation()),
//            cmd.getType()
//        )
//    ));
    List<DestinationCreationTO> destinations = List.of(new DestinationCreationTO(
        NameConvertor.toPointName(targetIds.get(1).getLocation()),
        cmd.getType()
    ));
    TransportOrderCreationTO orderTO
        = new TransportOrderCreationTO(NameConvertor.toCommandName(cmd.getParams().getUniqueID()), destinations)
        .withIntendedVehicleName(getVehicleName())
        .withType(cmd.getType());
    orderService.createTransportOrder(orderTO);
    dispatcherService.dispatch();
  }

  private void executeCharge(Command cmd) {
    // 参数校验
    List<Command.CommandParams.TargetID> targetIds
        = requireNonNull(cmd.getParams().getTargetID(), "targetIds");
    checkArgument(targetIds.size() == 1, "charge targetIds size should be 1");

    // 创建指令记录，并执行指令
    List<DestinationCreationTO> destinations = new ArrayList<>(1);
    String destName = NameConvertor.toRechargeName(targetIds.get(0).getLocation());
    String type = cmd.getParams().getCommand() == 1 ?
        Command.Type.CHARGE.getType() : Command.Type.STOP_CHARGE.getType();
    destinations.add(new DestinationCreationTO(destName, type));  // 终点
    TransportOrderCreationTO orderTO
        = new TransportOrderCreationTO(NameConvertor.toCommandName(cmd.getParams().getUniqueID()), destinations)
        .withIntendedVehicleName(getVehicleName())
        .withType(type);
    orderService.createTransportOrder(orderTO);
    if (type.equals(Command.Type.STOP_CHARGE.getType())
        && vehicleModel.getVehicleState().equals(Vehicle.State.CHARGING)) {
      vehicleModel.setVehicleState(Vehicle.State.IDLE);
    }
    dispatcherService.dispatch();
  }

  private void executeCancelPauseContinueLift(Command cmd) {
    // 创建已完成的指令记录，并执行指令
    List<DestinationCreationTO> destinations
        = List.of(new DestinationCreationTO(
        vehicleModel.getVehiclePosition() != null ? vehicleModel.getVehiclePosition() : "",
        OP_NOP));
    TransportOrderCreationTO orderTO
        = new TransportOrderCreationTO(NameConvertor.toCommandName(cmd.getParams().getUniqueID()), destinations)
        .withIntendedVehicleName(getVehicleName())
        .withType(cmd.getType());
    orderService.createFinishedTransportOrder(orderTO);
    Command.Type type = Command.Type.fromString(requireNonNull(cmd.getType(), "cmdType"));
    switch (type) {
      case CANCEL:
        dispatcherService.withdrawByVehicle(vehicleModel.getVehicleReference(), false);
        break;
      case PAUSE:
        vehicleService.updateVehiclePaused(vehicleModel.getVehicleReference(), true);
        break;
      case CONTINUE:
        vehicleService.updateVehiclePaused(vehicleModel.getVehicleReference(), false);
        break;
    }
    // 发送指令执行结果
    sendResult(
        cmd.getParams().getUniqueID(),
        type,
        Result.ErrorCode.SUCCEED.ordinal(),
        Result.ErrorReason.NONE.getValue(),
        null,
        false);
  }

  private void handleManagementCommand(Command cmd) {
    // 执行指令（不创建指令记录）, 并发送执行结果
    Command.Type type = Command.Type.fromString(requireNonNull(cmd.getType(), "cmdType"));
    switch (type) {
      case ONLINE:
        vehicleService.updateVehicleIntegrationLevel(
            vehicleModel.getVehicleReference(), Vehicle.IntegrationLevel.TO_BE_UTILIZED);
        sendResult(
            null,
            type,
            Result.ErrorCode.SUCCEED.ordinal(),
            Result.ErrorReason.NONE.getValue(),
            null,
            false);
        break;
      case OFFLINE:
        vehicleService.updateVehicleIntegrationLevel(
            vehicleModel.getVehicleReference(), Vehicle.IntegrationLevel.TO_BE_RESPECTED);
        sendResult(
            null,
            type,
            Result.ErrorCode.SUCCEED.ordinal(),
            Result.ErrorReason.NONE.getValue(),
            null,
            false);
        break;
      case SHUTDOWN:
        sendResult(
            null,
            type,
            Result.ErrorCode.SUCCEED.ordinal(),
            Result.ErrorReason.NONE.getValue(),
            null,
            true);
        vehicleService.disableCommAdapter(vehicleModel.getVehicleReference());
        break;
      default:
        LOG.warn("{}: Received unknown type command: {}", getVehicleName(), cmd);
    }
  }

  public boolean sendHeartbeat() {
    return send(MessageGenerator.generateHeartbeat(vehicleModel), false);
  }

  public boolean sendAck(@NonNull Response ack) {
    return send(ack, false);
  }

  public boolean sendResult(UnsignedLong uniqueId,
                            Command.Type type,
                            int errorCode,
                            int errorReason,
                            String actualBarcode,
                            boolean await) {
    Result result = MessageGenerator.generateResult(
        NameConvertor.toRobotId(getVehicleName()),
        uniqueId,
        type,
        errorCode,
        errorReason,
        actualBarcode,
        isNotInstantCommand(type.getType())
    );
    return send(result, await);
  }

  private boolean send(@NonNull Message msg, boolean await) {
    // 是否需要应答
    boolean needAck = msg.getHeader().getMsgMode() == Message.Header.Mode.ACK.getValue();
    // 将消息放入消息列表
    final int timeoutMillis = (needAck ?
        SocketConstants.SEND_TIMEOUT + SocketConstants.ACK_TIMEOUT : SocketConstants.SEND_TIMEOUT) * 1000;
    final MessageWrapper msgWrapper = new MessageWrapper(msg, timeoutMillis);
    if (msg.getChannel() != Message.Channel.DATA.ordinal() && msg.getParams().getUniqueID() != null)
      resendTable.put(msg.getParams().getUniqueID(), msgWrapper);
    // 打包消息并发送
    final ChannelFuture channelFuture = channel.writeAndFlush(msg);
    // 检查发送情况
    channelFuture.addListener((ChannelFutureListener) future -> {
      msgWrapper.setSendSuccess(true);
      msgWrapper.releaseSend();
    });
    if (await) {
      // 等待发送
      try {
        msgWrapper.awaitSend(SocketConstants.SEND_TIMEOUT, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        LOG.error(
            "{}: AwaitSend[type={}, uniqueID={}] exception: ",
            getVehicleName(), msg.getType(), msg.getParams().getUniqueID().toString(), e
        );
        return false;
      }
      // 等待应答
      if (needAck) {
        try {
          msgWrapper.awaitAck(SocketConstants.ACK_TIMEOUT, TimeUnit.SECONDS);
          return msgWrapper.isAckSuccess();
        } catch (InterruptedException e) {
          UnsignedLong uniqueID = msg.getParams().getUniqueID();
          LOG.error(
              "{}: AwaitAck[type={}, uniqueID={}] exception: ",
              getVehicleName(), msg.getType(), uniqueID.toString(), e
          );
          return false;
        }
      }
    }
    if (needAck) {
      return msgWrapper.isAckSuccess();
    } else {
      msgWrapper.setAckSuccess(true);
      return msgWrapper.isSendSuccess();
    }
  }

  public static int getReconnectDelayTime() {
//    return new Random().nextInt(5);
    return 2;
  }

  public static boolean isNotInstantCommand(String commandType) {
    return Arrays.stream(SocketConstants.INSTANT_COMMAND).noneMatch(type -> type.getType().equals(commandType));
  }

  @Data
  static class MessageWrapper {
    private final long beginTimestamp = System.currentTimeMillis();
    private final Message msg;
    private final long timeoutMillis;
    private final CountDownLatch sendCountDownLatch = new CountDownLatch(1);
    private final CountDownLatch ackCountDownLatch = new CountDownLatch(1);

    private volatile boolean sendSuccess = false;
    private volatile boolean ackSuccess = false;

    public MessageWrapper(Message msg, long timeoutMillis) {
      super();
      this.msg = msg;
      this.timeoutMillis = timeoutMillis;
    }

    public void awaitSend(int sendTimeout, TimeUnit unit) throws InterruptedException {
      sendCountDownLatch.await(sendTimeout, unit);
    }

    public void releaseSend() {
      sendCountDownLatch.countDown();
    }

    public void awaitAck(int ackTimeout, TimeUnit unit) throws InterruptedException {
      ackCountDownLatch.await(ackTimeout, unit);
    }

    public void releaseAck() {
      ackCountDownLatch.countDown();
    }

    public void releaseAll() {
      sendCountDownLatch.countDown();
      ackCountDownLatch.countDown();
    }
  }
}
