package org.opentcs.virtualvehicle.rms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import lombok.NonNull;
import org.opentcs.common.rms.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static java.util.Objects.requireNonNull;

class SocketClientInitializer extends ChannelInitializer<SocketChannel> {
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private final SocketClient socketClient;
  private final ScheduledThreadPoolExecutor scheduledTimer;

  public SocketClientInitializer(@NonNull SocketClient socketClient,
                                 @NonNull ScheduledThreadPoolExecutor scheduledTimer) {
    this.socketClient = requireNonNull(socketClient, "socketClient");
    this.scheduledTimer = scheduledTimer;
  }

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
    pipeline.addLast(new SocketClientHandler(socketClient, scheduledTimer));
  }

  private static class ClientMessageEncoder extends MessageToByteEncoder<Message> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
      // 报文数据区内容转为json格式
      String dataJson = DataToJson(msg);
//    byte[] jsonData = objectMapper.writeValueAsBytes(msg);

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
      if (msg.getParams().getUniqueID() != null)
        params.put("uniqueID", msg.getParams().getUniqueID());
      if (msg instanceof Heartbeat) {
        Heartbeat hb = (Heartbeat) msg;
        if (hb.getParams().getStatus() != null)
          params.put("status", hb.getParams().getStatus());
        if (hb.getParams().getPosition() != null)
          params.put("position", hb.getParams().getPosition());
        else
          params.put("position", 0);
        if (hb.getParams().getTheta() != null)
          params.put("theta", hb.getParams().getTheta());
        if (hb.getParams().getBatteryInfo() != null)
          params.put("batteryInfo", objectMapper.writeValueAsString(hb.getParams().getBatteryInfo()));
        if (hb.getParams().getOdo() != null)
          params.put("odo", hb.getParams().getOdo());
        if (hb.getParams().getToday_odo() != null)
          params.put("today_odo", hb.getParams().getToday_odo());
      } else if (msg instanceof Result) {
        Result result = (Result) msg;
        if (result.getParams().getBarcode() != null)
          params.put("barcode", result.getParams().getBarcode());
        if (result.getParams().getErrorCode() != null)
          params.put("errorCode", result.getParams().getErrorCode());
        if (result.getParams().getErrorReason() != null)
          params.put("errorReason", result.getParams().getErrorReason());
      }
      dataMap.put("params", objectMapper.writeValueAsString(params));
      return objectMapper.writeValueAsString(dataMap);
    }
  }

  private static class ClientMessageDecoder extends ByteToMessageDecoder {

    public static final Logger LOG = LoggerFactory.getLogger(ClientMessageDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
      // 报文序号字段
      String msgSeq = readString(in, 32);
      // 报文类型
      byte msgMode = in.readByte();
      // 报文数据区长度
      int msgLen = in.readIntLE();
      // CRC校验字段
      byte[] crc = readBytes(in, 2);
      // 保留字段
      byte[] reserved = readBytes(in, 2);
      Message.Header header = new Message.Header(msgSeq, msgMode, msgLen, crc, reserved);
      // 报文数据区内容
      String dataStr = readString(in, in.readableBytes());
      try {
        Message msg = JsonToData(dataStr, msgMode);
        if (msg == null) {
          throw new Exception("JsonToData returned null");
        }
        msg.setHeader(header);
        out.add(msg);
      } catch (Exception e) {
        LOG.error("Parse message data error：Data={}, Header={}\nError={}", dataStr, header, e.getMessage());
      }
    }

    private String readString(ByteBuf in, int length) {
      ByteBuf buf = in.readBytes(length);
      String str = buf.toString(CharsetUtil.UTF_8);
      buf.release();
      return str;
    }

    private byte[] readBytes(ByteBuf in, int length) {
      ByteBuf buf = in.readBytes(length);
      byte[] bytes = ByteBufUtil.getBytes(buf);
      buf.release();
      return bytes;
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
}
