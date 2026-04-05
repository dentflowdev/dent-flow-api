package com.dentalManagement.dentalFlowBackend.controller;


import com.dentalManagement.dentalFlowBackend.dto.request.CreateOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.UpdateOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.UpdateStageRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderHistoryResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderListResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.service.OrderService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
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
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    // ─────────────────────────────────────────────────────────
    // POST /api/orders
    // Marketing Executive creates a new order.
    // Status : ORDER_CREATED | Stage : null
    //
    // Accepts: multipart/form-data
    //   - "order" part : JSON string of CreateOrderRequest
    //   - "image" part : optional image file
    // ─────────────────────────────────────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrderResponse> createOrder(
            @RequestPart("order") String orderJson,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        log.info("POST /api/orders — raw order JSON: {}", orderJson);

        CreateOrderRequest request;
        try {
            request = objectMapper.readValue(orderJson, CreateOrderRequest.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order JSON: " + e.getMessage());
        }

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            violations.forEach(v -> log.error("Validation error — field: {}, message: {}",
                    v.getPropertyPath(), v.getMessage()));
            throw new ConstraintViolationException(violations);
        }

        OrderResponse response = orderService.createOrder(request, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/orders/{orderId}/stage
    // Technician updates the order to the next stage.
    //
    // First call  : ORDER_CREATED + null  → IN_PROGRESS + POURING
    // Middle calls: IN_PROGRESS + <stage> → IN_PROGRESS + <next stage>
    // Last call   : IN_PROGRESS + CERAMIC_LAYERING → READY + GLAZING (auto)
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{orderId}/stage")
    public ResponseEntity<OrderResponse> updateStage(
            @PathVariable UUID orderId,
            @RequestBody @Valid UpdateStageRequest request) {

        log.info("PATCH /api/orders/{}/stage — requestedStage: {}", orderId, request.getNewStage());
        return ResponseEntity.ok(orderService.updateStage(orderId, request));
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/orders/{orderId}/deliver
    // Marketing Executive marks a READY order as DELIVERED.
    // Status : DELIVERED | deliveredAt : set to now
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{orderId}/deliver")
    public ResponseEntity<OrderResponse> deliverOrder(@PathVariable UUID orderId) {

        log.info("PATCH /api/orders/{}/deliver", orderId);
        return ResponseEntity.ok(orderService.deliverOrder(orderId));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/orders/{orderId}
    // Fetch single order snapshot — all roles
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {

        log.info("GET /api/orders/{}", orderId);
        return ResponseEntity.ok(orderService.getOrderById(orderId));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/orders/{orderId}/history
    // Fetch full audit trail — Admin and Marketing Executive
    // ─────────────────────────────────────────────────────────
    @GetMapping("/{orderId}/history")
    public ResponseEntity<OrderHistoryResponse> getOrderHistory(@PathVariable UUID orderId) {

        log.info("GET /api/orders/{}/history", orderId);
        return ResponseEntity.ok(orderService.getOrderHistory(orderId));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/orders
    // List all orders — Admin and Marketing Executive
    // Optional query param : ?status=ORDER_CREATED|IN_PROGRESS|READY|DELIVERED
    // Paginated            : ?page=0&size=10
    // ─────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<OrderListResponse> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/orders — status: {}, page: {}, size: {}", status, page, size);
        return ResponseEntity.ok(orderService.getAllOrders(status, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/orders/search/{query}
    //
    // Search by barcode (exact) OR patient name (full/partial).
    // Priority: barcode → full name → partial name.
    // Paginated via ?page=0&size=10
    // ─────────────────────────────────────────────────────────
    @GetMapping("/search/{query}")
    public ResponseEntity<OrderListResponse> searchOrders(
            @PathVariable String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/orders/search/{} — page: {}, size: {}", query, page, size);
        return ResponseEntity.ok(orderService.searchOrders(query, page, size));
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/v1/orders/{orderId}
    // Admin deletes an order by ID.
    // ─────────────────────────────────────────────────────────
    @DeleteMapping("/delete/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID orderId) {

        log.info("DELETE /api/v1/orders/{}", orderId);
        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }
    // ─────────────────────────────────────────────────────────
    // GET /api/v1/orders/overdue
    // Returns all non-delivered orders where dueDate < now.
    // Paginated via ?page=0&size=10
    // ─────────────────────────────────────────────────────────
    @GetMapping("/overdue")
    public ResponseEntity<OrderListResponse> getOverdueOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/orders/overdue — page: {}, size: {}", page, size);
        return ResponseEntity.ok(orderService.getOverdueOrders(page, size));
    }
    // ─────────────────────────────────────────────────────────
    // PATCH /api/v1/orders/{orderId}/details
    // Update editable order fields. Sets isEdited = true.
    // Allowed roles: ADMIN, MARKETING_EXECUTIVE, RECEPTIONIST
    // No image — JSON body only.
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{orderId}/update")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderDetails(
            @PathVariable UUID orderId,
            @RequestBody @Valid UpdateOrderRequest request) {

        log.info("PATCH /api/v1/orders/{}/details", orderId);
        return ResponseEntity.ok(orderService.updateOrderDetails(orderId, request));
    }
}
