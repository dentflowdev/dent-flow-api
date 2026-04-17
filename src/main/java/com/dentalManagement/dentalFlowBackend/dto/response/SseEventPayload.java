package com.dentalManagement.dentalFlowBackend.dto.response;

/**
 * Wrapper serialized to JSON and pushed onto every Redis channel.
 *
 * Wire format example:
 * {
 *   "eventType": "ORDER_CREATED",
 *   "data": { ...OrderResponse fields... }
 * }
 *
 * The subscriber reads eventType to name the SSE event, and forwards
 * the entire JSON string as the SSE data payload to connected clients.
 */
public record SseEventPayload(String eventType, Object data) {}
