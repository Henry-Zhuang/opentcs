package org.opentcs.virtualvehicle.rms;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;
import org.opentcs.virtualvehicle.rms.message.Message;
import org.opentcs.virtualvehicle.rms.message.Response;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class SocketClientHandler extends SimpleChannelInboundHandler<Message> {
  private final SocketClient socketClient;
  private ScheduledFuture<?> hbFuture;
  private ScheduledFuture<?> resendFuture;

  public SocketClientHandler(@Nonnull SocketClient socketClient) {
    this.socketClient = requireNonNull(socketClient, "socketClient");
  }

  @Override
  public void channelActive(@Nonnull ChannelHandlerContext ctx) throws Exception {
    if (hbFuture == null || hbFuture.isCancelled()) {
      hbFuture = ctx.executor().scheduleAtFixedRate(
          socketClient::sendVehicleHeartbeat, 1000,
          SocketConstants.HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
      );
    }
    if (resendFuture == null || resendFuture.isCancelled()) {
      resendFuture = ctx.executor().scheduleAtFixedRate(
          socketClient::scanResendTable, 3 * 1000,
          SocketConstants.RESEND_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
      );
    }
  }

  @Override
  public void channelInactive(@Nonnull ChannelHandlerContext ctx) throws Exception {
    // 停止定时发送心跳任务
    if (hbFuture != null && !hbFuture.isCancelled()) {
      hbFuture.cancel(true);
    }
    // 停止定时查询应答任务
    if (resendFuture != null && !resendFuture.isCancelled()) {
      resendFuture.cancel(true);
    }
    // 重新连接至RMS
    SocketClient.LOG.info(String.format("%s: Reconnecting to RMS tcp server......", socketClient.getVehicleName()));
    ctx.executor().schedule(socketClient::connect, SocketClient.getReconnectDelayTime(), TimeUnit.SECONDS);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
    if (msg.getHeader().getMsgMode() == 0) {  // 收到应答信息
      socketClient.processReceivedAck(msg);
    } else { // 收到指令信息
      if (!SocketClient.isImmediateCommand(msg)) {
        // 非即时指令需要向RMS发送指令应答
        Response ack = new Response();
        ack.setDeviceType(msg.getDeviceType());
        ack.setChannel(msg.getChannel());
        ack.setType(msg.getType());
        Message.Params params = new Message.Params();
        params.setRobotID(msg.getParams().getRobotID());
        params.setUniqueID(msg.getParams().getUniqueID());
        ack.setParams(params);
        socketClient.sendAck(ack);
      }
      socketClient.processReceivedCommand(msg);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    SocketClient.LOG.error(String.format("%s: Exception caught: ", socketClient.getVehicleName()), cause);
    // 停止定时发送心跳任务
    if (hbFuture != null && !hbFuture.isCancelled()) {
      hbFuture.cancel(true);
    }
    // 停止定时查询应答任务
    if (resendFuture != null && !resendFuture.isCancelled()) {
      resendFuture.cancel(true);
    }
    ctx.fireExceptionCaught(cause);
  }
}
