package com.dentalManagement.dentalFlowBackend.controller;

import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.service.SseEmitterRegistry;
import com.dentalManagement.dentalFlowBackend.util.GetAuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * SSE subscription endpoint.
 *
 * GET /api/v1/sse/subscribe
 *   → requires valid JWT (any authenticated role)
 *   → returns a text/event-stream response that stays open
 *
 * On connect, the client immediately receives a "CONNECTED" event.
 * The connection is kept alive by a heartbeat comment (": ping") sent
 * every 25 seconds from SseEmitterRegistry.
 *
 * On disconnect (client closes app, network drop, token expiry), the
 * emitter's onCompletion/onTimeout/onError callbacks clean up the registry.
 */
@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final SseEmitterRegistry emitterRegistry;
    private final GetAuthenticatedUser getAuthenticatedUser;

    @Transactional(readOnly = true)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {

        User user = getAuthenticatedUser.execute();
        UUID userId = user.getId();

        // 55 min server-side timeout — just under Cloud Run's 60 min max request timeout.
        // Ensures Spring cleans up the emitter before Cloud Run forcibly closes the connection.
        // Client should reconnect on timeout.
        SseEmitter emitter = new SseEmitter(55 * 60 * 1000L);

        boolean isDoctor = user.getRoles().stream()
                .anyMatch(r -> r.getRoleName() == RoleName.ROLE_DOCTOR);

        if (isDoctor) {
            // Doctors receive only user-targeted events (order updates for their orders)
            emitterRegistry.registerDoctorUser(userId, emitter);

        } else {
            // Lab staff receive lab-wide broadcasts
            Lab lab = user.getPrimaryLab();
            if (lab == null) {
                log.warn("SSE: lab-staff user {} has no primary lab — closing connection", userId);
                emitter.completeWithError(
                        new IllegalStateException("No lab linked to this user account"));
                return emitter;
            }
            emitterRegistry.registerLabUser(userId, lab.getId(), emitter);
        }

        // Clean up registry when the connection ends for any reason
        emitter.onCompletion(() -> emitterRegistry.remove(userId));
        emitter.onTimeout(()   -> emitterRegistry.remove(userId));
        emitter.onError(e      -> emitterRegistry.remove(userId));

        // Send an immediate CONNECTED event so the client knows the stream is live
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("connected", MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            log.error("SSE: failed to send CONNECTED event to user {}: {}", userId, e.getMessage());
            emitterRegistry.remove(userId);
            emitter.completeWithError(e);
        }

        log.info("SSE: user {} subscribed (role={})",
                userId, isDoctor ? "DOCTOR" : "LAB_STAFF");

        return emitter;
    }
}
