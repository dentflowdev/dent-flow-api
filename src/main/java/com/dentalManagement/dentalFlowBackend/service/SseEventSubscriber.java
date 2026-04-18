package com.dentalManagement.dentalFlowBackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Listens to ALL "lab:*" and "user:*" Redis Pub/Sub channels.
 *
 * When a message arrives:
 *  1. Parse the channel name to determine target type (lab or user) and target ID.
 *  2. Extract the eventType string from the raw JSON body.
 *  3. Forward the raw JSON body (unchanged) to SseEmitterRegistry so it can
 *     push the exact same JSON to connected clients via SSE.
 *
 * The raw JSON is forwarded as-is — no re-serialization needed.
 * The client receives:
 *   event: ORDER_CREATED
 *   data: {"eventType":"ORDER_CREATED","data":{...}}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseEventSubscriber implements MessageListener {

    private final SseEmitterRegistry emitterRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body    = new String(message.getBody(),    StandardCharsets.UTF_8);

        log.info("SSE subscriber received message on channel: {}", channel);

        try {
            // Extract eventType from JSON without full deserialization
            JsonNode root = objectMapper.readTree(body);
            String eventType = root.path("eventType").asText();

            if (channel.startsWith("lab:")) {
                UUID labId = UUID.fromString(channel.substring(4));
                emitterRegistry.sendToLab(labId, eventType, body);

            } else if (channel.startsWith("user:")) {
                UUID userId = UUID.fromString(channel.substring(5));
                emitterRegistry.sendToUser(userId, eventType, body);

            } else {
                log.warn("SSE subscriber: unrecognised channel pattern: {}", channel);
            }

        } catch (Exception e) {
            log.error("SSE subscriber: failed to process message on channel {}: {}",
                    channel, e.getMessage());
        }
    }
}
