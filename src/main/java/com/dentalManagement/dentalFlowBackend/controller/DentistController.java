package com.dentalManagement.dentalFlowBackend.controller;

import com.dentalManagement.dentalFlowBackend.dto.request.CreateDentistOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.DailyOrderCountResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistAnalyticsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistLabResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderListResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderRequestResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderHistoryResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.service.DentistOrderRequestService;
import com.dentalManagement.dentalFlowBackend.service.DentistService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dentist")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('DOCTOR')")
public class DentistController {

    private final DentistService dentistService;
    private final DentistOrderRequestService dentistOrderRequestService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

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
    // GET /api/v1/dentist/orders/{orderId}/history
    // Returns the full audit trail for an order.
    // Order must belong to this dentist.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/orders/{orderId}/history")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {
        log.info("GET /api/v1/dentist/orders/{}/history", orderId);
        return ResponseEntity.ok(dentistService.getOrderHistory(orderId));
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

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/dentist/order-requests
    // Returns all pending order requests submitted by this doctor.
    // ─────────────────────────────────────────────────────────
    @GetMapping("/order-requests")
    public ResponseEntity<List<DentistOrderRequestResponse>> getMyOrderRequests() {
        log.info("GET /api/v1/dentist/order-requests");
        return ResponseEntity.ok(dentistOrderRequestService.getMyRequests());
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/dentist/order-requests
    // Doctor submits a partial order request to one of their linked labs.
    // Accepts multipart/form-data:
    //   - "request" part : JSON string of CreateDentistOrderRequest
    //   - "image"   part : optional image file
    // ─────────────────────────────────────────────────────────
    @PostMapping(value = "/order-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DentistOrderRequestResponse> createOrderRequest(
            @RequestPart("request") String requestJson,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        log.info("POST /api/v1/dentist/order-requests — raw JSON: {}", requestJson);

        CreateDentistOrderRequest request;
        try {
            request = objectMapper.readValue(requestJson, CreateDentistOrderRequest.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request JSON: " + e.getMessage());
        }

        Set<ConstraintViolation<CreateDentistOrderRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            violations.forEach(v -> log.error("Validation error — field: {}, message: {}",
                    v.getPropertyPath(), v.getMessage()));
            throw new ConstraintViolationException(violations);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dentistOrderRequestService.createRequest(request, image));
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/v1/dentist/order-requests/{requestId}
    // Called when the lab accepts the request — removes it from
    // the pending requests table.
    // ─────────────────────────────────────────────────────────
    @DeleteMapping("/order-requests/{requestId}")
    public ResponseEntity<Void> deleteOrderRequest(@PathVariable UUID requestId) {
        log.info("DELETE /api/v1/dentist/order-requests/{}", requestId);
        dentistOrderRequestService.deleteRequest(requestId);
        return ResponseEntity.noContent().build();
    }
}
