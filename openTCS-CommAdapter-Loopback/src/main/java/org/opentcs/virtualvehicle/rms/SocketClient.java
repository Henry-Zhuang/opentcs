package org.opentcs.virtualvehicle.rms;

import com.google.common.primitives.UnsignedLong;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import org.opentcs.components.kernel.services.TransportOrderService;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.VehicleProcessModel;
import org.opentcs.virtualvehicle.rms.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.opentcs.util.Assertions.checkInRange;

public class SocketClient {
  /**
   * This class's Logger.
   */
  public static final Logger LOG = LoggerFactory.getLogger(SocketClient.class);
  private final TransportOrderService orderService;
  private final VehicleProcessModel vehicleModel;
  private final String serverHost;
  private final int serverPort;
  private EventLoopGroup workerGroup;
  private Bootstrap bootstrap;
  private Channel channel;
  private volatile boolean enabled = false;
  protected ConcurrentHashMap<UnsignedLong, MessageWrapper> resendTable;

  public SocketClient(@Nonnull VehicleProcessModel vehicleModel,
                      @Nonnull TransportOrderService orderService,
                      @Nonnull String serverHost,
                      int serverPort) {
    this.vehicleModel = requireNonNull(vehicleModel, "vehicleModel");
    this.orderService = requireNonNull(orderService, "orderService");
    this.serverHost = requireNonNull(serverHost, "serverHost");
    this.serverPort = checkInRange(serverPort, 1, 65535, "serverPort");
  }

  public synchronized void enable() {
    if (isEnabled()) {
      return;
    }

    resendTable = new ConcurrentHashMap<UnsignedLong, MessageWrapper>(256);

    workerGroup = new NioEventLoopGroup(
        4, new DefaultThreadFactory(String.format("%s_SocketClient", vehicleModel.getName()), true)
    );
    bootstrap = new Bootstrap();

    bootstrap.group(workerGroup)
        .channel(NioSocketChannel.class)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, SocketConstants.CONNECT_TIMEOUT)
        .handler(new SocketClientInitializer(this));

    enabled = true;
    connect();
  }

  public synchronized void disable() {
    if (!isEnabled()) {
      return;
    }
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
        LOG.info("{}: Connected to RMS tcp server({}:{})", vehicleModel.getName(), serverHost, serverPort);
        channel = cf.channel();
      } else {
        if (!isEnabled()) {
          workerGroup.shutdownGracefully();
        } else {
          LOG.info("{}: Reconnecting to RMS tcp server......", vehicleModel.getName());
          workerGroup.schedule(this::connect, getReconnectDelayTime(), TimeUnit.SECONDS);
        }
      }
    } catch (InterruptedException ex) {
      LOG.error(String.format("%s: Connect to RMS tcp server exception: ", vehicleModel.getName()), ex);
    }
  }

  public synchronized void disconnect() {
    if (workerGroup.isShuttingDown()) {
      return;
    }

    Future<?> future = workerGroup.shutdownGracefully();
    try {
      future.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOG.error(String.format("%s: Disconnect exception: ", vehicleModel.getName()), e);
    }
    if (channel != null) {
      channel.close();
      LOG.info("{}: Disconnected from RMS tcp server({}:{})", vehicleModel.getName(), serverHost, serverPort);
    }
  }

  public String getVehicleName() {
    return vehicleModel.getName();
  }

  public void processReceivedAck(Message msg) {
    Response ack = msg instanceof Response ? (Response) msg : null;
    if (ack != null) {
      LOG.info("{}: Ack received:{}", vehicleModel.getName(), ack);
      final UnsignedLong uniqueID = ack.getParams().getUniqueID();
      if (uniqueID != null) {
        final MessageWrapper msgWrapper = resendTable.get(uniqueID);
        if (msgWrapper != null) {
          msgWrapper.setAckSuccess(true);
          msgWrapper.releaseAck();
          resendTable.remove(uniqueID);
        } else {
          LOG.warn(
              "{}: Ack for message[{}] was received but not required",
              vehicleModel.getName(),
              ack.getParams().getUniqueID()
          );
        }
      }
    }
  }

  public void processReceivedCommand(Message msg) {
    Command cmd = msg instanceof Command ? (Command) msg : null;
    if (cmd != null) {
      LOG.info("{}: Command received:{}", vehicleModel.getName(), cmd);

    }
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
            Message msg = msgWrapper.getMsg();
            boolean needAck = msg.getHeader().getMsgMode() == (byte) 1;
            send(msg, needAck, false);
            LOG.info(String.format("%s: Resend message: %s", vehicleModel.getName(), msg));
          }
        }
      }
    } catch (Throwable e) {
      LOG.error(String.format("%s: scanResendTable exception: ", vehicleModel.getName()), e);
    }
  }

  public void sendVehicleHeartbeat() {
    Heartbeat.HeartbeatParams hbParams = new Heartbeat.HeartbeatParams();

    hbParams.setRobotID(ObjectNameConvertor.toObjectId(Vehicle.class, vehicleModel.getName()));
    hbParams.setStatus(0);
    hbParams.setPosition(ObjectNameConvertor.toObjectId(Point.class, vehicleModel.getVehiclePosition()));
    double theta = vehicleModel.getVehicleOrientationAngle();
    if (!Double.isNaN(theta)) {
      hbParams.setTheta(theta);
    }
    hbParams.setOdo(10.0);
    hbParams.setToday_odo(20.0);
    Heartbeat.HeartbeatParams.BatteryInfo batteryInfo = new Heartbeat.HeartbeatParams.BatteryInfo();
    batteryInfo.setPercentage((double) vehicleModel.getVehicleEnergyLevel());
    hbParams.setBatteryInfo(batteryInfo);

    Heartbeat hb = new Heartbeat();
    hb.setDeviceType(1);
    hb.setChannel(2);
    hb.setType("heartbeat");
    hb.setParams(hbParams);
    sendHeartbeat(hb);
  }


  public boolean sendAck(@Nonnull Response ack) {
    return send(ack, false, false);
  }

  public boolean sendResult(@Nonnull Result result) {
    if (isImmediateCommand(result)) {  // 即时指令的执行结果不需要应答
      return send(result, false, false);
    } else {
      return send(result, true, false);
    }
  }

  private boolean sendHeartbeat(@Nonnull Heartbeat hb) {
    return send(hb, false, false);
  }

  private boolean send(@Nonnull Message msg, boolean needAck, boolean await) {
    // 添加报文头部
    final Message.Header header = new Message.Header();
    header.setMsgSeq(UUID.randomUUID().toString().replace("-", ""));
    header.setMsgMode(needAck ? (byte) 1 : (byte) 0);
    msg.setHeader(header);
    // 发送报文
    final ChannelFuture channelFuture = channel.writeAndFlush(msg);

    final int timeoutMillis = (needAck ?
        SocketConstants.SEND_TIMEOUT + SocketConstants.ACK_TIMEOUT : SocketConstants.SEND_TIMEOUT) * 1000;
    final MessageWrapper msgWrapper = new MessageWrapper(msg, timeoutMillis);

    channelFuture.addListener((ChannelFutureListener) future -> {
      msgWrapper.setSendSuccess(true);
      msgWrapper.releaseSend();
      if (msg.getChannel() != 2) {
        resendTable.put(msg.getParams().getUniqueID(), msgWrapper);
      }
    });
    if (await) {
      // 等待发送
      try {
        msgWrapper.awaitSend(SocketConstants.SEND_TIMEOUT, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        LOG.error(
            String.format(
                "%s: awaitSend[type=%s, uniqueID=%s] exception: ",
                vehicleModel.getName(),
                msg.getType(),
                msg.getParams().getUniqueID().toString()
            ), e);
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
              String.format(
                  "%s: awaitAck[type=%s, uniqueID=%s] exception: ",
                  vehicleModel.getName(),
                  msg.getType(),
                  uniqueID.toString()
              ), e);
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

  public static boolean isImmediateCommand(Message msg) {
    return msg.getChannel() == 1
        && Arrays.stream(SocketConstants.IMMEDIATE_COMMAND).noneMatch(type -> type.equals(msg.getType()));
  }

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

    public long getBeginTimestamp() {
      return beginTimestamp;
    }

    public long getTimeoutMillis() {
      return timeoutMillis;
    }

    public boolean isSendSuccess() {
      return sendSuccess;
    }

    public void setSendSuccess(boolean sendSuccess) {
      this.sendSuccess = sendSuccess;
    }

    public boolean isAckSuccess() {
      return ackSuccess;
    }

    public void setAckSuccess(boolean ackSuccess) {
      this.ackSuccess = ackSuccess;
    }

    public Message getMsg() {
      return msg;
    }
  }
}
