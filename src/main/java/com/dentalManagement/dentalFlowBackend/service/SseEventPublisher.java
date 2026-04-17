package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.response.SseEventPayload;
import com.dentalManagement.dentalFlowBackend.enums.SseEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Publishes SSE events onto Upstash Redis Pub/Sub channels.
 *
 * Two channel namespaces:
 *   "lab:{labId}"   → received by all lab-staff users connected to that lab
 *   "user:{userId}" → received by one specific user (doctor, or targeted admin action)
 *
 * Failure to publish (e.g. Redis temporarily unreachable) is caught and logged —
 * it never propagates to the calling service so the main API flow is not affected.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseEventPublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────

    /**
     * Broadcast an event to ALL lab-staff users belonging to the given lab.
     *
     * Used for: ORDER_CREATED, ORDER_STAGE_UPDATED, ORDER_DELIVERED,
     *           ORDER_UPDATED, ORDER_DELETED,
     *           DOCTOR_ADDED, DOCTOR_UPDATED, DOCTOR_DELETED,
     *           DENTIST_REQUEST_RECEIVED, DENTIST_REQUEST_REMOVED
     */
    public void publishToLab(UUID labId, SseEventType eventType, Object data) {
        publish("lab:" + labId, eventType, data);
    }

    /**
     * Send an event to ONE specific user by their userId.
     *
     * Used for: ORDER_STAGE_UPDATED / ORDER_DELIVERED / ORDER_UPDATED
     *           (targeted to the order's doctor),
     *           DENTIST_REQUEST_REMOVED (targeted to the requesting doctor),
     *           USER_DEACTIVATED, USER_DELETED, ROLE_CHANGED (targeted admin actions)
     */
    public void publishToUser(UUID userId, SseEventType eventType, Object data) {
        publish("user:" + userId, eventType, data);
    }

    // ─────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────

    private void publish(String channel, SseEventType eventType, Object data) {
        try {
            SseEventPayload payload = new SseEventPayload(eventType.name(), data);
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(channel, json);
            log.debug("SSE published — channel: {}, event: {}", channel, eventType);
        } catch (Exception e) {
            // Never let SSE failures break the main API response
            log.error("SSE publish failed — channel: {}, event: {}, error: {}",
                    channel, eventType, e.getMessage());
        }
    }
}
