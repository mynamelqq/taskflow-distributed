package com.taskflow.schedulerjava.executor;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

@Component
public class DelayEchoHandler implements TaskHandler {
    @Override
    public String taskType() {
        return "DELAY_ECHO";
    }
    @Override
    public JsonNode execute(JsonNode payload) throws Exception {

        if (payload == null
                || !payload.hasNonNull("message")
                || !payload.get("message").isTextual()) {
            throw new IllegalArgumentException(
                    "message must be a string"
            );
        }
        long delayMs = payload.path("delayMs").asLong(-1);//安全、链式的取值方法

        if (delayMs < 0 || delayMs > 10000) {
            throw new IllegalArgumentException(
                    "delayMs must be between 0 and 10000"
            );
        }

        String message = payload.get("message").asText();

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Task interrupted", e
            );
        }

        ObjectNode result =
                JsonNodeFactory.instance.objectNode();

        result.put("message", message);
        result.put("completedAt", Instant.now().toString());

        return result;
    }
}
