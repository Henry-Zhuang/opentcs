package org.opentcs.kernel.vehicles.rms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.UnsignedLong;
import com.google.inject.Inject;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import org.opentcs.components.Lifecycle;
import org.opentcs.data.model.Vehicle;

import org.opentcs.kernel.vehicles.rms.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.opentcs.util.Assertions.checkInRange;

public class SocketClient implements Lifecycle {
    /**
     * This class's Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SocketClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Vehicle vehicle;
    private final MessageGenerator messageGenerator;
    private final String serverHost;
    private final int serverPort;
    private EventLoopGroup workerGroup;
    private Bootstrap bootstrap;
    private Channel channel;
    private volatile boolean initialized = false;
    protected ConcurrentHashMap<UnsignedLong, MessageWrapper> resendTable;

    @Inject
    public SocketClient(@Nonnull Vehicle vehicle,
                        @Nonnull MessageGenerator messageGenerator,
                        @Nonnull String serverHost,
                        int serverPort) {
        this.vehicle = requireNonNull(vehicle, "vehicle");
        this.messageGenerator = requireNonNull(messageGenerator, "messageGenerator");
        this.serverHost = requireNonNull(serverHost, "serverHost");
        this.serverPort = checkInRange(serverPort, 1, 65535, "serverPort");
    }

    @Override
    public void initialize() {
        if (isInitialized()) {
            return;
        }

        resendTable = new ConcurrentHashMap<UnsignedLong, MessageWrapper>(256);

        this.workerGroup = new NioEventLoopGroup(
            4, new DefaultThreadFactory(String.format("%s_SocketClient", vehicle.getName()), true)
        );
        this.bootstrap = new Bootstrap();

        this.bootstrap.group(this.workerGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, SocketConstants.CONNECT_TIMEOUT)
            .handler(new SocketClientInitializer());
        this.initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void terminate() {
        if (!isInitialized()) {
            return;
        }
        this.initialized = false;
        disconnect();
        resendTable.clear();
        resendTable = null;
    }

    public void connect() {
        if (!isInitialized()) {
            return;
        }

        final ChannelFuture cf = bootstrap.connect(serverHost, serverPort);
        try {
            cf.await(SocketConstants.CONNECT_TIMEOUT, TimeUnit.SECONDS);
            if (cf.isSuccess()) {
                LOG.info("{}: Connected to RMS tcp server({}:{})", vehicle.getName(), serverHost, serverPort);
                this.channel = cf.channel();
            } else {
                if (!isInitialized()) {
                    this.workerGroup.shutdownGracefully();
                } else {
                    LOG.info("{}: Reconnecting to RMS tcp server......", vehicle.getName());
                    this.workerGroup.schedule(new ReconnectTask(), getReconnectDelayTime(), TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException ex) {
            LOG.error(String.format("%s: Connect to RMS tcp server exception: ", vehicle.getName()), ex);
        }
    }

    public void disconnect() {
        Future<?> future = this.workerGroup.shutdownGracefully();
        try {
            future.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error(String.format("%s: Disconnect exception: ", vehicle.getName()), e);
        }
        this.channel.close();
        LOG.info("{}: Disconnected from RMS tcp server({}:{})", vehicle.getName(), serverHost, serverPort);
    }

    public boolean sendHeartbeat(@Nonnull Heartbeat hb) {
        return send(hb, false, false);
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
                        vehicle.getName(),
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
                            vehicle.getName(),
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

    private int getReconnectDelayTime() {
        return new Random().nextInt(5);
    }

    private boolean isImmediateCommand(Message msg) {
        return msg.getChannel() == 1
            && Arrays.stream(SocketConstants.IMMEDIATE_COMMAND).noneMatch(type -> type.equals(msg.getType()));
    }

    private void processReceivedAck(Message msg) {
        Response ack = msg instanceof Response ? (Response) msg : null;
        if (ack != null) {
            LOG.info("{}: Ack received:{}", vehicle.getName(), ack);
            final UnsignedLong uniqueID = ack.getParams().getUniqueID();
            if (uniqueID != null) {
                final MessageWrapper msgWrapper = this.resendTable.get(uniqueID);
                if (msgWrapper != null) {
                    msgWrapper.setAckSuccess(true);
                    msgWrapper.releaseAck();
                    this.resendTable.remove(uniqueID);
                } else {
                    LOG.warn(
                        "{}: Ack for message[{}] was received but not required",
                        vehicle.getName(),
                        ack.getParams().getUniqueID()
                    );
                }
            }
        }
    }

    private void processReceivedCommand(Message msg) {
        Command cmd = msg instanceof Command ? (Command) msg : null;
        if (cmd != null) {
            LOG.info("{}: Command received:{}", vehicle.getName(), cmd);
        }
    }

    private class SocketClientInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new LengthFieldBasedFrameDecoder(
                ByteOrder.LITTLE_ENDIAN,
                Integer.MAX_VALUE,
                39,
                4,
                4,
                6,
                true
            ));
            pipeline.addLast(new ClientMessageEncoder());
            pipeline.addLast(new ClientMessageDecoder());
            pipeline.addLast(new SocketClientHandler());
        }
    }

    private class ClientMessageEncoder extends MessageToByteEncoder<Message> {
        @Override
        protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
            // 报文数据区内容转为json格式
            String dataJson = DataToJson(msg);
//            byte[] jsonData = objectMapper.writeValueAsBytes(msg);

            // 写入报文头部信息
            out.writeBytes(new byte[]{(byte) 0xAA, 0x55, (byte) 0xAA, 0X55, 0x01, 0x2F});
            out.writeBytes(msg.getHeader().getMsgSeq().getBytes());
            out.writeByte(msg.getHeader().getMsgMode());
            msg.getHeader().setMsgLen(dataJson.length());
            out.writeIntLE(msg.getHeader().getMsgLen());
            msg.getHeader().setCrc(new byte[]{0x00, 0x00});
            out.writeBytes(msg.getHeader().getCrc());
            msg.getHeader().setReserved(new byte[]{0x00, 0x00});
            out.writeBytes(msg.getHeader().getReserved());

            // 写入报文数据区内容(json)
            out.writeBytes(dataJson.getBytes(CharsetUtil.UTF_8));
        }

        private String DataToJson(Message msg) throws Exception {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("deviceType", msg.getDeviceType());
            dataMap.put("channel", msg.getChannel());
            dataMap.put("type", msg.getType());
            Map<String, Object> params = new HashMap<>();
            params.put("robotID", msg.getParams().getRobotID());
            params.put("uniqueID", msg.getParams().getUniqueID());
            if (msg instanceof Heartbeat) {
                Heartbeat hb = (Heartbeat) msg;
                params.put("status", hb.getParams().getStatus());
                params.put("position", hb.getParams().getPosition());
                params.put("theta", hb.getParams().getTheta());
                params.put("batteryInfo", objectMapper.writeValueAsString(hb.getParams().getBatteryInfo()));
                params.put("odo", hb.getParams().getOdo());
                params.put("today_odo", hb.getParams().getToday_odo());
            } else if (msg instanceof Result) {
                Result result = (Result) msg;
                params.put("barcode", result.getParams().getBarcode());
                params.put("errorCode", result.getParams().getErrorCode());
                params.put("errorReason", result.getParams().getErrorReason());
            }
            dataMap.put("params", objectMapper.writeValueAsString(params));
            return objectMapper.writeValueAsString(dataMap);
        }
    }

    private class ClientMessageDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            // 报文序号字段
            String msgSeq = in.readBytes(32).toString(CharsetUtil.UTF_8);
            // 报文类型
            byte msgMode = in.readByte();
            // 报文数据区长度
            int msgLen = in.readIntLE();
            // CRC校验字段
            byte[] crc = ByteBufUtil.getBytes(in.readBytes(2));
            // 保留字段
            byte[] reserved = ByteBufUtil.getBytes(in.readBytes(2));
            Message.Header header = new Message.Header(msgSeq, msgMode, msgLen, crc, reserved);
            // 报文数据区内容
            String dataStr = in.readBytes(in.readableBytes()).toString(CharsetUtil.UTF_8);
            Message msg = JsonToData(dataStr, msgMode);
            msg.setHeader(header);
            out.add(msg);
        }

        private Message JsonToData(String dataStr, byte msgMode) throws Exception {
            // 去除多余的反斜线及双引号
            dataStr = dataStr.replace("\\", "");
            dataStr = dataStr.replace("\"{", "{");
            dataStr = dataStr.replaceAll("}\\\\*\"", "}");

            return msgMode == 0 ?
                objectMapper.readValue(dataStr, Response.class) : objectMapper.readValue(dataStr, Command.class);
        }
    }

    private class SocketClientHandler extends SimpleChannelInboundHandler<Message> {
        private ScheduledFuture<?> hbFuture;
        private ScheduledFuture<?> resendFuture;

        @Override
        public void channelActive(@Nonnull ChannelHandlerContext ctx) throws Exception {
            if (hbFuture == null || hbFuture.isCancelled()) {
                this.hbFuture = ctx.executor().scheduleAtFixedRate(
                    new SendHeartbeatTask(), 1000,
                    SocketConstants.HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
                );
            }
            if (resendFuture == null || resendFuture.isCancelled()) {
                this.resendFuture = ctx.executor().scheduleAtFixedRate(
                    new ScanResendTableTask(), 3 * 1000,
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
            LOG.info(String.format("%s: Reconnecting to RMS tcp server......", vehicle.getName()));
            ctx.executor().schedule(new ReconnectTask(), getReconnectDelayTime(), TimeUnit.SECONDS);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
            if (msg.getHeader().getMsgMode() == 0) {  // 收到应答信息
                processReceivedAck(msg);
            } else { // 收到指令信息
                if (!isImmediateCommand(msg)) {
                    // 非即时指令需要向RMS发送指令应答
                    Response ack = new Response();
                    ack.setDeviceType(msg.getDeviceType());
                    ack.setChannel(msg.getChannel());
                    ack.setType(msg.getType());
                    Message.Params params = new Message.Params();
                    params.setRobotID(msg.getParams().getRobotID());
                    params.setUniqueID(msg.getParams().getUniqueID());
                    ack.setParams(params);
                    sendAck(ack);
                }
                processReceivedCommand(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LOG.error(String.format("%s: Exception caught: ", vehicle.getName()), cause);
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

    private class SendHeartbeatTask implements Runnable {
        @Override
        public void run() {
            try {
                sendVehicleHeartbeat();
            } catch (Throwable e) {
                LOG.error(String.format("%s: sendHeartbeat exception: ", vehicle.getName()), e);
            }
        }

        private void sendVehicleHeartbeat() {
            // Test
            Heartbeat.HeartbeatParams.BatteryInfo batteryInfo = new Heartbeat.HeartbeatParams.BatteryInfo(
                220.0,
                5.0,
                95.5,
                false,
                false,
                35.5,
                95.5
            );
            Heartbeat.HeartbeatParams hbParams = new Heartbeat.HeartbeatParams(
                0, 4012, 1.01, batteryInfo, 20.0, 10.0
            );
            hbParams.setRobotID(2);
            Heartbeat hb = new Heartbeat();
            hb.setParams(hbParams);
            hb.setDeviceType(1);
            hb.setChannel(2);
            hb.setType("heartbeat");
            SocketClient.this.sendHeartbeat(hb);
        }
    }

    private class ScanResendTableTask implements Runnable {
        @Override
        public void run() {
            try {
                scanResendTable();
            } catch (Throwable e) {
                LOG.error(String.format("%s: scanResendTable exception: ", vehicle.getName()), e);
            }
        }

        private void scanResendTable() {
            Iterator<Map.Entry<UnsignedLong, MessageWrapper>> it = SocketClient.this.resendTable.entrySet().iterator();
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
                        SocketClient.this.send(msg, needAck, false);
                        LOG.info(String.format("%s: Resend message: %s", vehicle.getName(), msg));
                    }
                }
            }
        }
    }

    private class ReconnectTask implements Runnable {
        @Override
        public void run() {
            SocketClient.this.connect();
        }
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
            this.sendCountDownLatch.await(sendTimeout, unit);
        }

        public void releaseSend() {
            this.sendCountDownLatch.countDown();
        }

        public void awaitAck(int ackTimeout, TimeUnit unit) throws InterruptedException {
            this.ackCountDownLatch.await(ackTimeout, unit);
        }

        public void releaseAck() {
            this.ackCountDownLatch.countDown();
        }

        public void releaseAll() {
            this.sendCountDownLatch.countDown();
            this.ackCountDownLatch.countDown();
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
