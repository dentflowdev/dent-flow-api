package com.dentalManagement.dentalFlowBackend.controller;

import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderRequestResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.LabDetailsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.LabRegistrationResponse;
import com.dentalManagement.dentalFlowBackend.service.DentistOrderRequestService;
import com.dentalManagement.dentalFlowBackend.service.LabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lab")
@RequiredArgsConstructor
@Slf4j
public class LabController {

    private final LabService labService;
    private final DentistOrderRequestService dentistOrderRequestService;

    /**
     * GET /api/v1/labs/me
     * Protected — requires a valid JWT.
     *
     * Intended to be called immediately after login by the frontend.
     * Resolves the lab from the authenticated user's JWT principal and returns:
     *   - Lab name, city, state
     *   - All material categories for that lab
     *   - All materials within each category
     *   - The ordered workflow stages for each category
     *
     * The frontend can cache this response for the session so it does not
     * need to fetch it again on every page load.
     */
    @GetMapping("/form-details")
    public ResponseEntity<LabDetailsResponse> getMyLabDetails() {
        LabDetailsResponse response = labService.getLabDetailsForCurrentUser();
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/lab/order-requests
    // Returns all pending dentist order requests sent to this lab.
    // Resolved from the authenticated lab user's primary lab.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/order-requests")
    public ResponseEntity<List<DentistOrderRequestResponse>> getDentistOrderRequests() {
        log.info("GET /api/v1/lab/order-requests");
        return ResponseEntity.ok(dentistOrderRequestService.getRequestsForCurrentLab());
    }
}
