package org.opentcs.kernel.vehicles.rms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.opentcs.data.model.Vehicle;
import org.opentcs.kernel.vehicles.rms.message.Heartbeat;
import org.opentcs.kernel.vehicles.rms.message.MessageGenerator;

import java.util.Scanner;

public class SocketClientTest {
    private static SocketClient client;
    private static volatile boolean run = true;

    @Test
    public void startup() {
        Vehicle vehicle = new Vehicle("Vehicle-001");
        client = new SocketClient(
            vehicle,
            new MessageGenerator(),
            SocketConstants.TCP_SERVER_IP,
            SocketConstants.TCP_SERVER_PORT
        );
        client.initialize();
        client.connect();
        while (run) ;
        client.terminate();
    }

    @AfterAll
    public static void shutdown() {
        if (client != null)
            client.terminate();
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
    public void replaceStr(){
        String dataStr = "{\"deviceType\": 1, \"channel\": 0, \"type\": \"move\", \"params\": \"{\\\"robotID\\\": 2, \\\"uniqueID\\\": 21, \\\"targetID\\\": [\\\"{\\\\\\\"location\\\\\\\": 4011, \\\\\\\"theta\\\\\\\": 0}\\\", \\\"{\\\\\\\"location\\\\\\\": 4011, \\\\\\\"theta\\\\\\\": 0}\\\"]}\"}";
        System.out.println(dataStr);
        dataStr = dataStr.replace("\\", "");
        dataStr = dataStr.replace("\"{", "{");
        dataStr = dataStr.replaceAll("}\\\\*\"", "}");
        System.out.println(dataStr);
    }
}
