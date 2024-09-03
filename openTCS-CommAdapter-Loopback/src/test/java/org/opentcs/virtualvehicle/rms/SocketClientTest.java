package org.opentcs.virtualvehicle.rms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.opentcs.common.rms.SocketConstants;
import org.opentcs.components.kernel.services.DispatcherService;
import org.opentcs.components.kernel.services.InternalTransportOrderService;
import org.opentcs.components.kernel.services.InternalVehicleService;
import org.opentcs.data.model.Vehicle;
import org.opentcs.util.event.SimpleEventBus;
import org.opentcs.common.rms.message.Heartbeat;
import org.opentcs.virtualvehicle.LoopbackVehicleModel;

import static org.mockito.Mockito.mock;

public class SocketClientTest {
  private static SocketClient client;
  private static volatile boolean run = true;

  @Test
  public void startup() {
    LoopbackVehicleModel vehicleModel = new LoopbackVehicleModel(
        new Vehicle("Vehicle-001"), 1000, -500, 50000,3600000
    );
    SocketClient
        client = new SocketClient(
        vehicleModel,
        new SimpleEventBus(),
        mock(InternalTransportOrderService.class),
        mock(DispatcherService.class),
        mock(InternalVehicleService.class),
        SocketConstants.DEFAULT_SERVER_IP,
        String.valueOf(SocketConstants.DEFAULT_SERVER_PORT),
        false
    );
    client.enable();
    while (run) ;
    client.disable();
  }

  @AfterAll
  public static void shutdown() {
    if (client != null)
      client.disable();
  }

  @Test
  public void heartbeatToJson() throws Exception {
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

    final ObjectMapper objectMapper = new ObjectMapper();
    String jsonData = objectMapper.writeValueAsString(hb);
    System.out.println(jsonData);
    Heartbeat h = objectMapper.readValue(jsonData, Heartbeat.class);
    System.out.println(objectMapper.writeValueAsString(h));

//        String jsonStr = "{\"deviceType\": 1, \"channel\": 0, \"type\": \"move\", \"params\": \"{\\"robotID\\": 1, \\"uniqueID\\": 15, \\"targetID\\    ": [\"{\\\"location\\\": 4012, \\\"theta\\\": 0}\", \"{\\\"location\\\": 4013, \\\"theta\\\": 0}\"]}\"}";
//        JsonNode node = objectMapper.readTree(jsonStr);
//        Heartbeat heartbeat = objectMapper.readValue(jsonStr, Heartbeat.class);
  }

  @Test
  public void replaceStr() {
    String dataStr = "{\"deviceType\": 1, \"channel\": 0, \"type\": \"move\", \"params\": \"{\\\"robotID\\\": 2, \\\"uniqueID\\\": 21, \\\"targetID\\\": [\\\"{\\\\\\\"location\\\\\\\": 4011, \\\\\\\"theta\\\\\\\": 0}\\\", \\\"{\\\\\\\"location\\\\\\\": 4011, \\\\\\\"theta\\\\\\\": 0}\\\"]}\"}";
    System.out.println(dataStr);
    dataStr = dataStr.replace("\\", "");
    dataStr = dataStr.replace("\"{", "{");
    dataStr = dataStr.replaceAll("}\\\\*\"", "}");
    System.out.println(dataStr);
  }
}
