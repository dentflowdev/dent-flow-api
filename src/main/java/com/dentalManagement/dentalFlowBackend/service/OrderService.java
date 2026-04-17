package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.request.CreateOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.UpdateOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.UpdateStageRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderHistoryResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderListResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import com.dentalManagement.dentalFlowBackend.enums.SseEventType;
import com.dentalManagement.dentalFlowBackend.exception.DuplicateBarcodeException;
import com.dentalManagement.dentalFlowBackend.exception.InvalidTransitionException;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.Order;
import com.dentalManagement.dentalFlowBackend.model.OrderHistory;
import com.dentalManagement.dentalFlowBackend.model.Role;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.model.LabWorkflow;
import com.dentalManagement.dentalFlowBackend.objectMapper.OrderMapper;
import com.dentalManagement.dentalFlowBackend.model.DentistOrderRequest;
import com.dentalManagement.dentalFlowBackend.repository.DentistOrderRequestRepository;
import com.dentalManagement.dentalFlowBackend.repository.DoctorRepository;
import com.dentalManagement.dentalFlowBackend.repository.OrderHistoryRepository;
import com.dentalManagement.dentalFlowBackend.repository.OrderRepository;
import com.dentalManagement.dentalFlowBackend.repository.UserRepository;
import com.dentalManagement.dentalFlowBackend.util.GetAuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DentistOrderRequestRepository dentistOrderRequestRepository;
    private final OrderStateMachine stateMachine;
    private final OrderMapper orderMapper;
    private final CloudStorageService cloudStorageService;
    private final LabWorkflowService labWorkflowService;
    private final GetAuthenticatedUser getAuthenticatedUser;
    private final SseEventPublisher ssePublisher;
    // ─────────────────────────────────────────────────────────
    // CREATE ORDER (Marketing Executive)
    // Status  : ORDER_CREATED
    // Stage   : null
    // Workflow: Resolved from selected materials
    // ─────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, MultipartFile image) {

        log.info("Creating order with barcodeId: {}", request.getBarcodeId());

        // 1. Validate barcode uniqueness
        if (orderRepository.existsByBarcodeId(request.getBarcodeId())) {
            throw new DuplicateBarcodeException("Barcode ID already exists: " + request.getBarcodeId());
        }

        // 2. Get authenticated user from JWT
        User createdBy = getAuthenticatedUser.execute();

        // 3. Resolve Doctor from doctorId (moved up — image upload deferred after DB save)
        Doctor doctor = doctorRepository.findById(request.getClinicalDetails().getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor not found: " + request.getClinicalDetails().getDoctorId()));

        // 5. RESOLVE WORKFLOW from selected materials
        // Get lab from authenticated user (assuming user belongs to a lab)
        UUID labId = createdBy.getPrimaryLab() != null ? createdBy.getPrimaryLab().getId() : null;
        LabWorkflow workflow = null;

        if (labId != null && request.getClinicalDetails().getMaterials() != null) {
            try {
                workflow = labWorkflowService.resolveWorkflowFromMaterials(
                        request.getClinicalDetails().getMaterials(),
                        labId
                );
                log.info("Resolved workflow for order {}: {}",
                        request.getBarcodeId(),
                        workflow != null ? workflow.getWorkflowName() : "DEFAULT");
            } catch (Exception e) {
                log.warn("Failed to resolve workflow, will use default at stage update: {}", e.getMessage());
                workflow = null;
            }
        }
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDateTime createdAtIST = LocalDateTime.now(istZone);

        // 6. Map nested request DTO → flat Order entity (no imageUrl yet — upload happens after save)
        Order order = Order.builder()
                .barcodeId(request.getBarcodeId())
                // Case Details
                .caseNumber(request.getCaseDetails().getCaseNumber())
                .boxNumber(request.getCaseDetails().getBoxNumber())
                .dueDate(request.getCaseDetails().getDueDate())
                .deliverySchedule(request.getCaseDetails().getDeliverySchedule())
                .orderType(request.getCaseDetails().getOrderType())
                // Patient Details
                .patientName(request.getPatientDetails().getName())
                // Clinical Details
                .doctor(doctor)
                .teeth(request.getClinicalDetails().getTeeth())
                .shade(request.getClinicalDetails().getShade())
                .materials(request.getClinicalDetails().getMaterials())
                // Additional Details
                .instructions(request.getAdditionalDetails() != null
                        ? request.getAdditionalDetails().getInstructions() : null)
                // Image — set after DB save to avoid orphaned uploads on rollback
                .imageUrl(null)
                // Status — always starts at ORDER_CREATED with no stage
                .currentStatus(OrderStatus.ORDER_CREATED)
                .currentStage(null)
                // Workflow
                .workflow(workflow)
                // Audit
                .createdBy(createdBy)
                .createdAt(createdAtIST)
                .build();

        // 7. Save order to DB first — if this fails, no image is uploaded
        Order savedOrder = orderRepository.save(order);

        // 8. Image handling — two paths:
        //    a) Doctor-placed order with pre-uploaded imageUrl → assign directly, no upload
        //    b) Normal flow with multipart image file → upload to cloud storage
        boolean doctorPlaced = Boolean.TRUE.equals(request.getOrderPlacedByDoctor());

        if (doctorPlaced && request.getImageUrl() != null) {
            log.info("Doctor-placed order: assigning pre-uploaded imageUrl for order: {}", request.getBarcodeId());
            savedOrder.setImageUrl(request.getImageUrl());
            savedOrder = orderRepository.save(savedOrder);
        } else if (image != null && !image.isEmpty()) {
            log.info("Uploading image for order: {}", request.getBarcodeId());
            try {
                String imageUrl = cloudStorageService.uploadImage(image);
                savedOrder.setImageUrl(imageUrl);
                savedOrder = orderRepository.save(savedOrder);
            } catch (Exception e) {
                log.error("Image upload failed for order {}. Rolling back DB record. Error: {}",
                        request.getBarcodeId(), e.getMessage());
                orderRepository.deleteById(savedOrder.getId());
                throw new RuntimeException("Image upload failed: " + e.getMessage());
            }
        }

        // 8b. If this order was converted from a DentistOrderRequest, delete that request.
        //     Fetch first to capture the doctor's userId for SSE, then delete.
        UUID dentistRequestDoctorUserId = null;
        if (doctorPlaced && request.getDentistOrderRequestId() != null) {
            Optional<DentistOrderRequest> dentistRequestOpt =
                    dentistOrderRequestRepository.findById(request.getDentistOrderRequestId());
            if (dentistRequestOpt.isPresent()) {
                DentistOrderRequest dentistReq = dentistRequestOpt.get();
                // Capture BEFORE delete so we can notify the doctor via SSE
                if (dentistReq.getRequestedBy() != null) {
                    dentistRequestDoctorUserId = dentistReq.getRequestedBy().getId();
                }
                dentistOrderRequestRepository.delete(dentistReq);
                log.info("Deleted DentistOrderRequest {} after converting to order",
                        request.getDentistOrderRequestId());
            }
        }

        // 9. Record first history entry
        recordHistory(savedOrder, createdBy,
                null, null,
                OrderStatus.ORDER_CREATED, null,
                "Order created");

        log.info("Order created. ID: {}, BarcodeId: {}, Workflow: {}",
                savedOrder.getId(), savedOrder.getBarcodeId(),
                workflow != null ? workflow.getWorkflowName() : "DEFAULT");

        OrderResponse createdResponse = orderMapper.toOrderResponse(savedOrder);

        // ── SSE: notify lab staff of new order ──────────────────
        if (labId != null) {
            ssePublisher.publishToLab(labId, SseEventType.ORDER_CREATED, createdResponse);
        }
        // ── SSE: notify the doctor whose request was just converted ──
        if (dentistRequestDoctorUserId != null) {
            ssePublisher.publishToUser(dentistRequestDoctorUserId,
                    SseEventType.DENTIST_REQUEST_REMOVED,
                    Map.of("requestId", request.getDentistOrderRequestId()));
        }

        return createdResponse;
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE STAGE (Technician)
    //
    // This method handles the entire technician journey across all workflow types:
    //
    //  ORDER_CREATED + stage null  → Technician sets first stage
    //                                 status becomes IN_PROGRESS
    //
    //  IN_PROGRESS + stage[N]      → Technician sets stage[N+1]
    //                                 status stays IN_PROGRESS
    //  ... (continues through all stages of the workflow) ...
    //
    //  IN_PROGRESS + stage[last]   → Technician sets final stage
    //                                 status auto-becomes READY
    //
    // WORKFLOW HANDLING:
    //   - If order has workflow attached: use it
    //   - If order has no workflow (old order): use default (Crown & Bridge)
    // ─────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse updateStage(UUID orderId, UpdateStageRequest request) {

        log.info("Updating stage for order {}. Requested stage: {}", orderId, request.getNewStage());

        Order order = findOrderById(orderId);
        User technician = getAuthenticatedUser.execute();
        validateOrderBelongsToLab(order, technician.getPrimaryLab());

        // Get the workflow to use (provided or default)
        LabWorkflow effectiveWorkflow = order.getWorkflow();

        // Validate the transition is legal via dynamic state machine
        stateMachine.validateStageTransition(
                order.getCurrentStatus(),
                order.getCurrentStage() != null ? order.getCurrentStage().toString() : null,
                request.getNewStage().toString(),
                effectiveWorkflow
        );

        // Capture previous state before mutating
        OrderStatus prevStatus = order.getCurrentStatus();
        String prevStageStr = order.getCurrentStage() != null ? order.getCurrentStage().toString() : null;

        // Determine new status:
        //  - Setting first stage (ORDER_CREATED) → IN_PROGRESS
        //  - Setting final stage                  → READY (auto transition)
        //  - All other stages                     → stays IN_PROGRESS
        OrderStatus newStatus;
        if (prevStatus == OrderStatus.ORDER_CREATED) {
            newStatus = OrderStatus.IN_PROGRESS;
        } else if (stateMachine.isFinalStage(request.getNewStage().toString(), effectiveWorkflow)) {
            newStatus = OrderStatus.READY;
        } else {
            newStatus = OrderStatus.IN_PROGRESS;
        }

        order.setCurrentStatus(newStatus);
        order.setCurrentStage(request.getNewStage());

        Order updatedOrder = orderRepository.save(order);

        // Record history entry
        recordHistory(updatedOrder, technician,
                prevStatus, prevStageStr != null ? prevStageStr : null,
                newStatus, request.getNewStage().toString(),
                "Stage updated to " + request.getNewStage());

        log.info("Order {} stage updated to {}. Status: {} → {}",
                orderId, request.getNewStage(), prevStatus, newStatus);

        OrderResponse stageResponse = orderMapper.toOrderResponse(updatedOrder);

        // ── SSE: all lab staff ──
        ssePublisher.publishToLab(technician.getPrimaryLab().getId(),
                SseEventType.ORDER_STAGE_UPDATED, stageResponse);

        // ── SSE: the order's doctor (only if they have a linked User account) ──
        Doctor stageDoctor = updatedOrder.getDoctor();
        if (stageDoctor != null && stageDoctor.getUser() != null) {
            ssePublisher.publishToUser(stageDoctor.getUser().getId(),
                    SseEventType.ORDER_STAGE_UPDATED, stageResponse);
        }

        return stageResponse;
    }

    // ─────────────────────────────────────────────────────────
    // DELIVER ORDER (Marketing Executive)
    // Status : READY → DELIVERED
    // ─────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse deliverOrder(UUID orderId) {

        log.info("Marking order {} as delivered", orderId);

        Order order = findOrderById(orderId);
        User deliveredBy = getAuthenticatedUser.execute();
        validateOrderBelongsToLab(order, deliveredBy.getPrimaryLab());

        if (order.getCurrentStatus() != OrderStatus.READY) {
            throw new InvalidTransitionException(
                    "Order must be READY to deliver. Current status: " + order.getCurrentStatus()
            );
        }

        OrderStatus prevStatus = order.getCurrentStatus();

        order.setCurrentStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        Order updatedOrder = orderRepository.save(order);

        recordHistory(updatedOrder, deliveredBy,
                prevStatus, null,
                OrderStatus.DELIVERED, null,
                "Order delivered");

        log.info("Order {} delivered at {}", orderId, order.getDeliveredAt());

        OrderResponse deliveredResponse = orderMapper.toOrderResponse(updatedOrder);

        // ── SSE: all lab staff ──
        ssePublisher.publishToLab(deliveredBy.getPrimaryLab().getId(),
                SseEventType.ORDER_DELIVERED, deliveredResponse);

        // ── SSE: the order's doctor (only if linked to a User account) ──
        Doctor deliverDoctor = updatedOrder.getDoctor();
        if (deliverDoctor != null && deliverDoctor.getUser() != null) {
            ssePublisher.publishToUser(deliverDoctor.getUser().getId(),
                    SseEventType.ORDER_DELIVERED, deliveredResponse);
        }

        return deliveredResponse;
    }

    // ─────────────────────────────────────────────────────────
    // GET SINGLE ORDER
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {

        log.info("Fetching order {}", orderId);

        Order order = findOrderById(orderId);
        User currentUser = getAuthenticatedUser.execute();
        if (!orderBelongsToLab(order, currentUser.getPrimaryLab())) {
            log.info("Order {} does not belong to lab of user {}", orderId, currentUser.getUsername());
            return null;
        }
        return orderMapper.toOrderResponseWithWorkflow(order);
    }

    // ─────────────────────────────────────────────────────────
    // GET ORDER HISTORY
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderHistoryResponse getOrderHistory(UUID orderId) {

        log.info("Fetching order history for {}", orderId);

        Order order = findOrderById(orderId);
        User currentUser = getAuthenticatedUser.execute();
        if (!orderBelongsToLab(order, currentUser.getPrimaryLab())) {
            log.info("Order {} does not belong to lab of user {} — returning empty history", orderId, currentUser.getUsername());
            return orderMapper.toOrderHistoryResponse(orderId, List.of());
        }

        // Fetch history - oldest first (as per user requirement)
        List<OrderHistory> historyList = orderHistoryRepository.findAllByOrderIdOrderByChangedAtAsc(orderId);
        return orderMapper.toOrderHistoryResponse(orderId, historyList);
    }

    // ─────────────────────────────────────────────────────────
    // LIST ALL ORDERS (with optional status filter)
    // Paginated: ?page=0&size=10
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderListResponse getAllOrders(OrderStatus status, int page, int size) {

        log.info("Fetching all orders — status: {}, page: {}, size: {}", status, page, size);

        User currentUser = getAuthenticatedUser.execute();
        Lab currentLab = currentUser.getPrimaryLab();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Order> orderPage = status != null
                ? orderRepository.findAllByCurrentStatusAndCreatedByLab(status, currentLab, pageable)
                : orderRepository.findAllByCreatedByLab(currentLab, pageable);

        log.info("Found {} order(s)", orderPage.getTotalElements());

        List<OrderResponse> responses = orderPage.getContent()
                .stream()
                .map(orderMapper::toOrderResponse)
                .collect(Collectors.toList());

        return OrderListResponse.builder()
                .orders(responses)
                .currentPage(orderPage.getNumber())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // SEARCH ORDERS
    // Priority: barcode (exact) → patient name (full/partial)
    // Paginated: ?page=0&size=10
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderListResponse searchOrders(String query, int page, int size) {

        log.info("Searching orders with query: '{}', page: {}, size: {}", query, page, size);

        User currentUser = getAuthenticatedUser.execute();
        Lab currentLab = currentUser.getPrimaryLab();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // ── Priority 1: Exact barcode match within same lab ──
        Optional<Order> barcodeMatch = orderRepository.findByBarcodeIdAndCreatedByLab(query, currentLab);

        if (barcodeMatch.isPresent()) {
            log.info("Exact barcode match found for query: '{}'", query);
            List<OrderResponse> responses = List.of(orderMapper.toOrderResponse(barcodeMatch.get()));
            return OrderListResponse.builder()
                    .orders(responses)
                    .currentPage(0)
                    .totalPages(1)
                    .totalElements(1)
                    .build();
        }

        log.info("No barcode match for '{}'. Falling back to patient name search.", query);

        // ── Priority 2 + 3: patient name OR doctor name match within same lab ──
        Page<Order> orderPage = orderRepository.findAllByPatientNameOrDoctorNameContainingIgnoreCaseAndLab(query, currentLab, pageable);

        log.info("{} match — {} result(s) found for query: '{}'",
                orderPage.hasContent() ? "Patient name" : "No",
                orderPage.getTotalElements(), query);

        List<OrderResponse> responses = orderPage.getContent()
                .stream()
                .map(orderMapper::toOrderResponse)
                .collect(Collectors.toList());

        return OrderListResponse.builder()
                .orders(responses)
                .currentPage(orderPage.getNumber())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // DELETE ORDER (Admin only)
    // ─────────────────────────────────────────────────────────

    @Transactional
    public void deleteOrder(UUID orderId) {

        log.info("Deleting order: {}", orderId);

        Order order = findOrderById(orderId);
        User currentUser = getAuthenticatedUser.execute();
        validateOrderBelongsToLab(order, currentUser.getPrimaryLab());

        // Capture labId before deletion for SSE
        UUID deleteLabId = currentUser.getPrimaryLab().getId();

        // Delete all history records for this order first (FK constraint)
        int deletedHistoryCount = orderHistoryRepository.deleteAllByOrderId(orderId);
        log.info("Deleted {} history record(s) for order: {}", deletedHistoryCount, orderId);

        orderRepository.deleteById(orderId);
        log.info("Order {} successfully deleted", orderId);

        // ── SSE: lab staff see the order disappear ──
        ssePublisher.publishToLab(deleteLabId, SseEventType.ORDER_DELETED,
                Map.of("orderId", orderId));
    }

    // ─────────────────────────────────────────────────────────
    // GET OVERDUE ORDERS
    // Returns orders with status ORDER_CREATED, IN_PROGRESS, or READY
    // where dueDate < now — paginated.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderListResponse getOverdueOrders(int page, int size) {

        log.info("Fetching overdue orders — page: {}, size: {}", page, size);

        User currentUser = getAuthenticatedUser.execute();
        Lab currentLab = currentUser.getPrimaryLab();
        Pageable pageable = PageRequest.of(page, size, Sort.by("dueDate").ascending());

        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.ORDER_CREATED,
                OrderStatus.IN_PROGRESS,
                OrderStatus.READY
        );
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDateTime startOfDayIST = LocalDateTime.now(istZone).toLocalDate().atStartOfDay();

        log.info("Comparing overdue orders before: {}", startOfDayIST);
        Page<Order> orderPage = orderRepository.findOverdueOrdersByLab(activeStatuses,
                startOfDayIST, currentLab, pageable);

        log.info("Found {} overdue order(s)", orderPage.getTotalElements());

        List<OrderResponse> responses = orderPage.getContent()
                .stream()
                .map(orderMapper::toOrderResponse)
                .collect(Collectors.toList());

        return OrderListResponse.builder()
                .orders(responses)
                .currentPage(orderPage.getNumber())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE ORDER DETAILS (with workflow change handling)
    //
    // Allowed fields: boxNumber, dueDate, deliverySchedule,
    //                 orderType, patientName, doctorId,
    //                 teeth, shade, materials, instructions
    //
    // SPECIAL HANDLING FOR MATERIALS:
    //   - If materials field is provided:
    //     ✓ Resolve the new workflow from the new materials
    //     ✓ If new workflow differs from current:
    //       → Update order with new workflow
    //       → Clear all order history except the ORDER_CREATED entry
    //       → Reset stage to null and status to ORDER_CREATED
    //     ✓ If new workflow is same:
    //       → Just update materials, keep history intact
    // ─────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse updateOrderDetails(UUID orderId, UpdateOrderRequest request) {

        log.info("Updating order details for orderId: {}", orderId);

        Order order = findOrderById(orderId);
        User updatedBy = getAuthenticatedUser.execute();
        validateOrderBelongsToLab(order, updatedBy.getPrimaryLab());

        // ── Check if materials are being changed ──
        boolean materialsChanged = request.getMaterials() != null &&
                !Objects.equals(order.getMaterials(), request.getMaterials());

        LabWorkflow newWorkflow = order.getWorkflow();

        if (materialsChanged) {
            log.info("Materials changed for order {}. Checking for workflow change.", orderId);

            // Resolve workflow from the new materials
            UUID labId = order.getCreatedBy().getPrimaryLab() != null
                    ? order.getCreatedBy().getPrimaryLab().getId()
                    : null;

            if (labId != null) {
                try {
                    newWorkflow = labWorkflowService.resolveWorkflowFromMaterials(
                            request.getMaterials(),
                            labId
                    );

                    // Check if workflow actually changed
                    UUID oldWorkflowId = order.getWorkflow() != null ? order.getWorkflow().getId() : null;
                    UUID newWorkflowId = newWorkflow != null ? newWorkflow.getId() : null;

                    if (!Objects.equals(oldWorkflowId, newWorkflowId)) {
                        log.warn("Workflow changed for order {}. Old: {}, New: {}",
                                orderId,
                                oldWorkflowId != null ? oldWorkflowId : "NULL",
                                newWorkflowId != null ? newWorkflowId : "NULL");

                        // ── WORKFLOW CHANGED: Reset progress ──
                        handleWorkflowChange(order, newWorkflow);
                    } else {
                        log.info("Materials changed but workflow remains the same for order {}", orderId);
                    }

                } catch (Exception e) {
                    log.warn("Failed to resolve workflow for new materials: {}", e.getMessage());
                    newWorkflow = order.getWorkflow(); // Keep existing workflow
                }
            }
        }

        // ── Apply only non-null fields — null = "don't change this field" ──
        if (request.getBoxNumber() != null) order.setBoxNumber(request.getBoxNumber());
        if (request.getDueDate() != null) order.setDueDate(request.getDueDate());
        if (request.getDeliverySchedule() != null) order.setDeliverySchedule(request.getDeliverySchedule());
        if (request.getOrderType() != null) order.setOrderType(request.getOrderType());
        if (request.getPatientName() != null) order.setPatientName(request.getPatientName());
        if (request.getDoctorId() != null) {
            Doctor updatedDoctor = doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.getDoctorId()));
            order.setDoctor(updatedDoctor);
        }
        if (request.getTeeth() != null) order.setTeeth(request.getTeeth());
        if (request.getShade() != null) order.setShade(request.getShade());
        if (request.getMaterials() != null) order.setMaterials(request.getMaterials());
        if (request.getInstructions() != null) order.setInstructions(request.getInstructions());

        // Mark as edited — irreversible flag
        order.setEdited(true);

        // Update workflow if it changed
        if (newWorkflow != order.getWorkflow()) {
            order.setWorkflow(newWorkflow);
        }

        Order updated = orderRepository.save(order);

        log.info("Order {} details updated. isEdited=true", orderId);

        OrderResponse updatedResponse = orderMapper.toOrderResponse(updated);

        // ── SSE: all lab staff ──
        ssePublisher.publishToLab(updatedBy.getPrimaryLab().getId(),
                SseEventType.ORDER_UPDATED, updatedResponse);

        // ── SSE: the order's doctor (only if linked to a User account) ──
        Doctor updatedOrderDoctor = updated.getDoctor();
        if (updatedOrderDoctor != null && updatedOrderDoctor.getUser() != null) {
            ssePublisher.publishToUser(updatedOrderDoctor.getUser().getId(),
                    SseEventType.ORDER_UPDATED, updatedResponse);
        }

        return updatedResponse;
    }

    // ─────────────────────────────────────────────────────────
    // HELPER: Handle workflow change during order update
    // ─────────────────────────────────────────────────────────

    private void handleWorkflowChange(Order order, LabWorkflow newWorkflow) {

        log.info("Handling workflow change for order {}. " +
                        "Resetting stage/status and clearing history except ORDER_CREATED.",
                order.getId());

        // Step 1: Reset order state to initial
        order.setWorkflow(newWorkflow);
        order.setCurrentStatus(OrderStatus.ORDER_CREATED);
        order.setCurrentStage(null);

        // Step 3: Save the order with reset state
        orderRepository.save(order);

        // Step 4: Clear all intermediate history (keep only initial ORDER_CREATED entries)
        orderHistoryRepository.deleteAllByOrderIdExceptOrderCreated(order.getId());

        log.info("Order {} workflow change completed. History cleaned.", order.getId());
    }

    // ─────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────

    private Order findOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private boolean orderBelongsToLab(Order order, Lab userLab) {
        Lab orderLab = order.getCreatedBy().getPrimaryLab();
        return orderLab != null && userLab != null && orderLab.getId().equals(userLab.getId());
    }

    private void validateOrderBelongsToLab(Order order, Lab userLab) {
        if (!orderBelongsToLab(order, userLab)) {
            throw new ResourceNotFoundException("No order found in this lab");
        }
    }


    /**
     * Records an immutable history entry for every state/stage mutation.
     */
    private void recordHistory(Order order, User changedBy,
                               OrderStatus prevStatus, String prevStageStr,
                               OrderStatus newStatus, String newStageStr,
                               String remarks) {

        String roleAtTime = changedBy.getRoles().stream()
                .map(Role::getRoleName)
                .filter(role -> role != RoleName.ROLE_DEFAULT_USER)
                .findFirst()
                .orElse(RoleName.ROLE_DEFAULT_USER)
                .toString();

        OrderHistory history = OrderHistory.builder()
                .order(order)
                .changedBy(changedBy)
                .roleAtTime(roleAtTime)
                .previousStatus(prevStatus)
                .previousStage(prevStageStr)
                .newStatus(newStatus)
                .newStage(newStageStr)
                .remarks(remarks)
                .changedAt(LocalDateTime.now())
                .build();

        orderHistoryRepository.save(history);
    }
}