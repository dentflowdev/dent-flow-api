package com.dentalManagement.dentalFlowBackend.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory registry of all active SSE connections.
 *
 * Two user types are tracked differently:
 *
 *   Lab staff  → registered under labId.
 *               Receive events published to "lab:{labId}" channel.
 *
 *   Doctors    → registered under userId only.
 *               Receive events published to "user:{userId}" channel.
 *
 * A heartbeat comment (": ping") is sent every 25 seconds to every open
 * connection to prevent load-balancers and mobile OS from closing idle sockets.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    // userId → open SSE connection
    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    // userId → labId  (only populated for lab-staff users, never for doctors)
    private final ConcurrentHashMap<UUID, UUID> userLabMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    // ─────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────

    @PostConstruct
    public void startHeartbeat() {
        // Send a comment every 25 s to keep connections alive.
        // 25 s < typical 30 s idle-connection timeout on reverse proxies.
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeatToAll, 25, 25, TimeUnit.SECONDS);
        log.info("SSE heartbeat scheduler started (interval: 25s)");
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
        log.info("SSE heartbeat scheduler stopped");
    }

    // ─────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────

    /**
     * Called from SseController when a lab-staff user (ADMIN, TECHNICIAN,
     * MARKETING_EXEC, RECEPTIONIST) opens an SSE connection.
     */
    public void registerLabUser(UUID userId, UUID labId, SseEmitter emitter) {
        emitters.put(userId, emitter);
        userLabMap.put(userId, labId);
        log.info("SSE: lab-staff user {} connected (lab={}). Total connections: {}",
                userId, labId, emitters.size());
    }

    /**
     * Called from SseController when a ROLE_DOCTOR user opens an SSE connection.
     * Doctors are NOT placed in userLabMap — they only receive user-targeted events.
     */
    public void registerDoctorUser(UUID userId, SseEmitter emitter) {
        emitters.put(userId, emitter);
        log.info("SSE: doctor user {} connected. Total connections: {}", userId, emitters.size());
    }

    /**
     * Removes the emitter and its lab mapping.
     * Called on completion, timeout, error, or explicit disconnect.
     */
    public void remove(UUID userId) {
        emitters.remove(userId);
        userLabMap.remove(userId);
        log.info("SSE: user {} disconnected. Total connections: {}", userId, emitters.size());
    }

    // ─────────────────────────────────────────────────────────
    // Dispatch — called by SseEventSubscriber
    // ─────────────────────────────────────────────────────────

    /**
     * Sends rawJson to every lab-staff user connected for the given labId.
     * Doctors are intentionally excluded — they receive only via sendToUser().
     */
    public void sendToLab(UUID labId, String eventType, String rawJson) {
        userLabMap.forEach((userId, userLab) -> {
            if (labId.equals(userLab)) {
                sendToEmitter(userId, eventType, rawJson);
            }
        });
    }

    /**
     * Sends rawJson to the specific user identified by userId.
     * Used for doctor order notifications, USER_DEACTIVATED, ROLE_CHANGED, etc.
     */
    public void sendToUser(UUID userId, String eventType, String rawJson) {
        sendToEmitter(userId, eventType, rawJson);
    }

    // ─────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────

    private void sendToEmitter(UUID userId, String eventType, String rawJson) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.warn("SSE: no active emitter for user {} — event {} dropped", userId, eventType);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(rawJson, MediaType.APPLICATION_JSON));
            log.info("SSE: sent {} to user {}", eventType, userId);
        } catch (IOException e) {
            log.warn("SSE: send failed for user {} ({}), removing connection", userId, e.getMessage());
            remove(userId);
            emitter.completeWithError(e);
        }
    }

    private void sendHeartbeatToAll() {
        if (emitters.isEmpty()) return;

        // Snapshot the keyset so we don't mutate while iterating
        List<UUID> userIds = new ArrayList<>(emitters.keySet());
        int sent = 0;

        for (UUID userId : userIds) {
            SseEmitter emitter = emitters.get(userId);
            if (emitter == null) continue;
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                sent++;
            } catch (IOException e) {
                log.debug("SSE: heartbeat failed for user {}, removing", userId);
                remove(userId);
                emitter.completeWithError(e);
            }
        }
        log.debug("SSE: heartbeat sent to {} connection(s)", sent);
    }
}
