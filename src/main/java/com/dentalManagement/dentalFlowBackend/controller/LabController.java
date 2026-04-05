package com.dentalManagement.dentalFlowBackend.controller;

import com.dentalManagement.dentalFlowBackend.dto.response.LabDetailsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.LabRegistrationResponse;
import com.dentalManagement.dentalFlowBackend.service.LabService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lab")
@RequiredArgsConstructor
public class LabController {

    private final LabService labService;

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
}
