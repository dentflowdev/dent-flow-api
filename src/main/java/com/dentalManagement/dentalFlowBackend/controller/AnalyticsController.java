package com.dentalManagement.dentalFlowBackend.controller;


import com.dentalManagement.dentalFlowBackend.dto.response.DailyOrderCountResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistAnalyticsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DoctorOrderCountResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.StageCountDtoResponse;
import com.dentalManagement.dentalFlowBackend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE', 'RECEPTIONIST', 'TECHNICIAN')")
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

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/analytics/orders/daily-count
    // Returns order counts per day for the last 30 days (IST).
    // All 30 days are always present; days with no orders have count 0.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/daily-count")
    public ResponseEntity<List<DailyOrderCountResponse>> getDailyOrderCounts() {
        log.info("GET /api/v1/analytics/orders/daily-count");
        return ResponseEntity.ok(analyticsService.getDailyOrderCounts());
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/analytics/doctors/leaderboard
    // Returns all doctors in the lab with their order count
    // for the current month (1st of month → today, IST).
    // Sorted high → low. Doctors with 0 orders are excluded.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/doctors/leaderboard")
    public ResponseEntity<List<DoctorOrderCountResponse>> getDoctorOrderCountsCurrentMonth() {
        log.info("GET /api/v1/analytics/doctors/leaderboard");
        return ResponseEntity.ok(analyticsService.getDoctorOrderCountsCurrentMonth());
    }


}
