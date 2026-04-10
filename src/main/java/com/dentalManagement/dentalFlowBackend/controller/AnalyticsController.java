package com.dentalManagement.dentalFlowBackend.controller;


import com.dentalManagement.dentalFlowBackend.dto.response.DentistAnalyticsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.StageCountDtoResponse;
import com.dentalManagement.dentalFlowBackend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/stages/in-progress")
    public ResponseEntity<List<StageCountDtoResponse>> getInProgressStageCount() {
        log.info("Request: Get stage counts for orders IN_PROGRESS");
        List<StageCountDtoResponse> stageCounts = analyticsService.getStageCountsForInProgress();
        return ResponseEntity.ok(stageCounts);
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/analytics/orders/summary
    // Returns total orders, per-status counts (ORDER_CREATED,
    // IN_PROGRESS, READY, DELIVERED), and overdue count
    // scoped to the authenticated user's primary lab.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/count")
    public ResponseEntity<DentistAnalyticsResponse> getOrderSummaryCounts() {
        log.info("GET /api/v1/analytics/orders/summary");
        return ResponseEntity.ok(analyticsService.getOrderSummaryCounts());
    }


}
