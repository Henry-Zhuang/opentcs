package org.opentcs.kernel.vehicles.rms.message;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageGenerator {
    private final ObjectMapper objectMapper;

    public MessageGenerator() {
        this.objectMapper = new ObjectMapper();
    }
}
