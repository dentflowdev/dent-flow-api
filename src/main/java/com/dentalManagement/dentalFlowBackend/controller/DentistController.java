package com.dentalManagement.dentalFlowBackend.controller;

import com.dentalManagement.dentalFlowBackend.dto.response.DailyOrderCountResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistAnalyticsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistLabResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderListResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.service.DentistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dentist")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('DOCTOR')")
public class DentistController {

    private final DentistService dentistService;

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/analytics
    // Returns order counts by status, overdue count, and total.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/analytics/order/count")
    public ResponseEntity<DentistAnalyticsResponse> getAnalytics() {
        log.info("GET /api/v1/dentist/analytics");
        return ResponseEntity.ok(dentistService.getAnalytics());
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/labs
    // Returns all labs this dentist is linked to.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/labs")
    public ResponseEntity<List<DentistLabResponse>> getLinkedLabs() {
        log.info("GET /api/v1/dentist/labs");
        return ResponseEntity.ok(dentistService.getLinkedLabs());
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/orders
    // Returns paginated orders across all linked labs.
    // Optional filter: ?status=ORDER_CREATED|IN_PROGRESS|READY|DELIVERED
    // Pagination: ?page=0&size=10
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders")
    public ResponseEntity<DentistOrderListResponse> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/dentist/orders — status: {}, page: {}, size: {}", status, page, size);
        return ResponseEntity.ok(dentistService.getAllOrders(status, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/orders/search/{query}
    // Search by barcode (exact) or patient name (partial).
    // Pagination: ?page=0&size=10
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/search/{query}")
    public ResponseEntity<DentistOrderListResponse> searchOrders(
            @PathVariable String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/dentist/orders/search/{} — page: {}, size: {}", query, page, size);
        return ResponseEntity.ok(dentistService.searchOrders(query, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/orders/overdue
    // Returns non-delivered orders where dueDate < today.
    // Pagination: ?page=0&size=10
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/overdue")
    public ResponseEntity<DentistOrderListResponse> getOverdueOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/dentist/orders/overdue — page: {}, size: {}", page, size);
        return ResponseEntity.ok(dentistService.getOverdueOrders(page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/orders/lab/{labId}
    // Returns paginated orders for a specific lab.
    // Pagination: ?page=0&size=10
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/lab/{labId}")
    public ResponseEntity<DentistOrderListResponse> getAllOrdersByLabId(
            @PathVariable UUID labId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/dentist/orders/lab/{} — status: {}, page: {}, size: {}", labId, status, page, size);
        return ResponseEntity.ok(dentistService.getAllOrdersByLabId(labId, status, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/orders/{orderId}
    // Returns a single order. Must belong to this dentist.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<DentistOrderResponse> getOrderById(@PathVariable UUID orderId) {
        log.info("GET /api/v1/dentist/orders/{}", orderId);
        return ResponseEntity.ok(dentistService.getOrderById(orderId));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/orders/daily-count
    // Returns order counts per day for the last 30 days (IST).
    // Counts across all labs the dentist is linked to.
    // All 30 days present; days with no orders have count 0.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/daily-count")
    public ResponseEntity<List<DailyOrderCountResponse>> getDailyOrderCounts() {
        log.info("GET /api/v1/dentist/orders/daily-count");
        return ResponseEntity.ok(dentistService.getDailyOrderCounts());
    }
}
