package org.opentcs.virtualvehicle.rms;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.NonNull;
import org.opentcs.common.rms.SocketConstants;
import org.opentcs.common.rms.message.Message;
import org.opentcs.common.rms.message.MessageGenerator;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SocketClientHandler extends SimpleChannelInboundHandler<Message> {
  private final SocketClient socketClient;
  private final ScheduledThreadPoolExecutor scheduledTimer;
  private ScheduledFuture<?> hbFuture;
  private ScheduledFuture<?> resendFuture;

  public SocketClientHandler(@NonNull SocketClient socketClient,
                             @NonNull ScheduledThreadPoolExecutor scheduledTimer) {
    this.socketClient = socketClient;
    this.scheduledTimer = scheduledTimer;
  }

  @Override
  public void channelActive(@NonNull ChannelHandlerContext ctx) {
    if (hbFuture == null || hbFuture.isCancelled()) {
      hbFuture = scheduledTimer.scheduleAtFixedRate(
          socketClient::sendHeartbeat,
          0, SocketConstants.HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
      );
    }
    if (resendFuture == null || resendFuture.isCancelled()) {
      resendFuture = scheduledTimer.scheduleAtFixedRate(
          socketClient::scanResendTable,
          3 * 1000, SocketConstants.RESEND_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
      );
    }
  }

  @Override
  public void channelInactive(@NonNull ChannelHandlerContext ctx) {
    // 停止定时发送心跳任务
    if (hbFuture != null) {
      hbFuture.cancel(true);
    }
    // 停止定时查询应答任务
    if (resendFuture != null) {
      resendFuture.cancel(true);
    }
    // 重新连接至RMS
    if (socketClient.isEnabled()){
      SocketClient.LOG.info(String.format("%s: Reconnecting to RMS tcp server......", socketClient.getVehicleName()));
      ctx.executor().schedule(socketClient::connect, SocketClient.getReconnectDelayTime(), TimeUnit.SECONDS);
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
    try {
      if (msg.getHeader().getMsgMode() == 0)   // 收到应答信息
        socketClient.processReceivedAck(msg);
      else { // 收到指令信息
        if (SocketClient.isNotInstantCommand(msg.getType()))
          // 非即时指令需要向RMS发送指令应答
          socketClient.sendAck(MessageGenerator.generateAck(msg));
        socketClient.processReceivedCommand(msg);
      }
    } catch (Throwable e) {
      SocketClient.LOG.error("{}: Exception caught: ", socketClient.getVehicleName(), e);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    SocketClient.LOG.error("{}: Exception caught: ", socketClient.getVehicleName(), cause);
    // 停止定时发送心跳任务
    if (hbFuture != null) {
      hbFuture.cancel(true);
    }
    // 停止定时查询应答任务
    if (resendFuture != null) {
      resendFuture.cancel(true);
    }
    ctx.fireExceptionCaught(cause);
  }
}
