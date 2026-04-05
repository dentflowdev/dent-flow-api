package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.request.CreateOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.UpdateOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.UpdateStageRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderHistoryResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderListResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import com.dentalManagement.dentalFlowBackend.exception.DuplicateBarcodeException;
import com.dentalManagement.dentalFlowBackend.exception.InvalidTransitionException;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.Order;
import com.dentalManagement.dentalFlowBackend.model.OrderHistory;
import com.dentalManagement.dentalFlowBackend.model.Role;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.model.LabWorkflow;
import com.dentalManagement.dentalFlowBackend.objectMapper.OrderMapper;
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
    private final OrderStateMachine stateMachine;
    private final OrderMapper orderMapper;
    private final CloudStorageService cloudStorageService;
    private final LabWorkflowService labWorkflowService;
    private final GetAuthenticatedUser getAuthenticatedUser;
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

        // 3. Handle optional image upload
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            log.info("Uploading image for order: {}", request.getBarcodeId());
            try {
                imageUrl = cloudStorageService.uploadImage(image);
            } catch (Exception e) {
                log.error("Image upload failed for order {}: {}", request.getBarcodeId(), e.getMessage());
                throw new RuntimeException("Image upload failed: " + e.getMessage());
            }
        }

        // 4. RESOLVE WORKFLOW from selected materials
        // Get lab from authenticated user (assuming user belongs to a lab)
        UUID labId = createdBy.getLab() != null ? createdBy.getLab().getId() : null;
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
        // 5. Map nested request DTO → flat Order entity
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
                .doctorName(request.getClinicalDetails().getDoctor())
                .teeth(request.getClinicalDetails().getTeeth())
                .shade(request.getClinicalDetails().getShade())
                .materials(request.getClinicalDetails().getMaterials())
                // Additional Details
                .instructions(request.getAdditionalDetails() != null
                        ? request.getAdditionalDetails().getInstructions() : null)
                // Image
                .imageUrl(imageUrl)
                // Status — always starts at ORDER_CREATED with no stage
                .currentStatus(OrderStatus.ORDER_CREATED)
                .currentStage(null)
                // Workflow (NEW)
                .workflow(workflow)
                // Audit
                .createdBy(createdBy)
                .createdAt(createdAtIST)  // ✅ Explicit IST
                .build();

        Order savedOrder = orderRepository.save(order);

        // 6. Record first history entry
        recordHistory(savedOrder, createdBy,
                null, null,
                OrderStatus.ORDER_CREATED, null,
                "Order created");

        log.info("Order created. ID: {}, BarcodeId: {}, Workflow: {}",
                savedOrder.getId(), savedOrder.getBarcodeId(),
                workflow != null ? workflow.getWorkflowName() : "DEFAULT");

        return orderMapper.toOrderResponse(savedOrder);
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

        // Get the workflow to use (provided or default)
        LabWorkflow effectiveWorkflow = order.getWorkflow();

        // Validate the transition is legal via dynamic state machine
        stateMachine.validateStageTransition(
                order.getCurrentStatus(),
                order.getCurrentStage() != null ? order.getCurrentStage().toString() : null,
                request.getNewStage().toString(),
                effectiveWorkflow
        );

        User technician = getAuthenticatedUser.execute();

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

        return orderMapper.toOrderResponse(updatedOrder);
    }

    // ─────────────────────────────────────────────────────────
    // DELIVER ORDER (Marketing Executive)
    // Status : READY → DELIVERED
    // ─────────────────────────────────────────────────────────

    @Transactional
    public OrderResponse deliverOrder(UUID orderId) {

        log.info("Marking order {} as delivered", orderId);

        Order order = findOrderById(orderId);

        if (order.getCurrentStatus() != OrderStatus.READY) {
            throw new InvalidTransitionException(
                    "Order must be READY to deliver. Current status: " + order.getCurrentStatus()
            );
        }

        User deliveredBy = getAuthenticatedUser.execute();

        OrderStatus prevStatus = order.getCurrentStatus();

        order.setCurrentStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        Order updatedOrder = orderRepository.save(order);

        recordHistory(updatedOrder, deliveredBy,
                prevStatus, null,
                OrderStatus.DELIVERED, null,
                "Order delivered");

        log.info("Order {} delivered at {}", orderId, order.getDeliveredAt());

        return orderMapper.toOrderResponse(updatedOrder);
    }

    // ─────────────────────────────────────────────────────────
    // GET SINGLE ORDER
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {

        log.info("Fetching order {}", orderId);

        Order order = findOrderById(orderId);
        return orderMapper.toOrderResponseWithWorkflow(order);
    }

    // ─────────────────────────────────────────────────────────
    // GET ORDER HISTORY
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderHistoryResponse getOrderHistory(UUID orderId) {

        log.info("Fetching order history for {}", orderId);

        // Verify order exists
        findOrderById(orderId);

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

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Order> orderPage = status != null
                ? orderRepository.findAllByCurrentStatus(status, pageable)
                : orderRepository.findAll(pageable);

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

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // ── Priority 1: Exact barcode match ──
        Optional<Order> barcodeMatch = orderRepository.findByBarcodeId(query);

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

        // ── Priority 2 + 3: patient name OR doctor name match ──
        Page<Order> orderPage = orderRepository.findAllByPatientNameOrDoctorNameContainingIgnoreCase(query, pageable);

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

        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        // Delete all history records for this order first (FK constraint)
        int deletedHistoryCount = orderHistoryRepository.deleteAllByOrderId(orderId);
        log.info("Deleted {} history record(s) for order: {}", deletedHistoryCount, orderId);

        orderRepository.deleteById(orderId);
        log.info("Order {} successfully deleted", orderId);
    }

    // ─────────────────────────────────────────────────────────
    // GET OVERDUE ORDERS
    // Returns orders with status ORDER_CREATED, IN_PROGRESS, or READY
    // where dueDate < now — paginated.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderListResponse getOverdueOrders(int page, int size) {

        log.info("Fetching overdue orders — page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("dueDate").ascending());

        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.ORDER_CREATED,
                OrderStatus.IN_PROGRESS,
                OrderStatus.READY
        );
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDateTime startOfDayIST = LocalDateTime.now(istZone).toLocalDate().atStartOfDay();

        log.info("Comparing overdue orders before: {}", startOfDayIST);
        Page<Order> orderPage = orderRepository.findOverdueOrders(activeStatuses,
                startOfDayIST, pageable);

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
    //                 orderType, patientName, doctorName,
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

        // ── Check if materials are being changed ──
        boolean materialsChanged = request.getMaterials() != null &&
                !Objects.equals(order.getMaterials(), request.getMaterials());

        LabWorkflow newWorkflow = order.getWorkflow();

        if (materialsChanged) {
            log.info("Materials changed for order {}. Checking for workflow change.", orderId);

            // Resolve workflow from the new materials
            UUID labId = order.getCreatedBy().getLab() != null
                    ? order.getCreatedBy().getLab().getId()
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
                        handleWorkflowChange(order, newWorkflow, updatedBy);
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
        if (request.getDoctorName() != null) order.setDoctorName(request.getDoctorName());
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

        return orderMapper.toOrderResponse(updated);
    }

    // ─────────────────────────────────────────────────────────
    // HELPER: Handle workflow change during order update
    // ─────────────────────────────────────────────────────────

    private void handleWorkflowChange(Order order, LabWorkflow newWorkflow, User changedBy) {

        log.info("Handling workflow change for order {}. " +
                        "Resetting stage/status and clearing history except ORDER_CREATED.",
                order.getId());

        // Step 1: Capture previous state for history record
        OrderStatus prevStatus = order.getCurrentStatus();
        String prevStage = order.getCurrentStage();

        // Step 2: Reset order state to initial
        order.setWorkflow(newWorkflow);
        order.setCurrentStatus(OrderStatus.ORDER_CREATED);
        order.setCurrentStage(null);

        // Step 3: Save the order with reset state
        orderRepository.save(order);

        // Step 4: Record the workflow change in history
        recordHistory(order, changedBy,
                prevStatus, prevStage,
                OrderStatus.ORDER_CREATED, null,
                "Materials changed → Workflow updated. Order progress reset to ORDER_CREATED.");

        // Step 5: Clear all intermediate history (keep only initial ORDER_CREATED entries)
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