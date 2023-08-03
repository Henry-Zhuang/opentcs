package org.opentcs.virtualvehicle.rms.message;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageGenerator {
    private final ObjectMapper objectMapper;

    public MessageGenerator() {
        this.objectMapper = new ObjectMapper();
    }
}
