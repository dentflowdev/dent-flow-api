package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.response.DailyOrderCountResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistAnalyticsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistLabResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderListResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.exception.OperationNotPermittedException;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.model.Order;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.repository.DoctorRepository;
import com.dentalManagement.dentalFlowBackend.repository.OrderRepository;
import com.dentalManagement.dentalFlowBackend.util.GetAuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DentistService {

    private final DoctorRepository doctorRepository;
    private final OrderRepository orderRepository;
    private final GetAuthenticatedUser getAuthenticatedUser;

    // ─────────────────────────────────────────────────────────
    // GET LINKED LABS
    // Returns all labs this dentist is associated with.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DentistLabResponse> getLinkedLabs() {
        User currentUser = getAuthenticatedUser.execute();
        log.info("Fetching linked labs for dentist: {}", currentUser.getUsername());

        return doctorRepository.findByUser(currentUser)
                .stream()
                .map(doctor -> DentistLabResponse.builder()
                        .id(doctor.getLab().getId())
                        .name(doctor.getLab().getName())
                        .address(doctor.getLab().getAddress())
                        .city(doctor.getLab().getCity())
                        .state(doctor.getLab().getState())
                        .pincode(doctor.getLab().getPincode())
                        .mobileNumber(doctor.getLab().getMobileNumber())
                        .email(doctor.getLab().getEmail())
                        .orderCount(orderRepository.countByDoctorIn(List.of(doctor)))
                        .build())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────
    // GET ALL ORDERS
    // Returns paginated orders across all linked labs,
    // optionally filtered by status. Latest first.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DentistOrderListResponse getAllOrders(OrderStatus status, int page, int size) {
        User currentUser = getAuthenticatedUser.execute();
        log.info("Fetching all orders for dentist: {} — status: {}, page: {}, size: {}",
                currentUser.getUsername(), status, page, size);

        List<Doctor> doctors = doctorRepository.findByUser(currentUser);
        if (doctors.isEmpty()) {
            log.info("Dentist {} has no linked labs — returning empty list", currentUser.getUsername());
            return emptyListResponse(page);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Order> orderPage = status != null
                ? orderRepository.findAllByDoctorInAndCurrentStatus(doctors, status, pageable)
                : orderRepository.findAllByDoctorIn(doctors, pageable);

        log.info("Found {} order(s) for dentist: {}", orderPage.getTotalElements(), currentUser.getUsername());
        return toDentistOrderListResponse(orderPage);
    }

    // ─────────────────────────────────────────────────────────
    // SEARCH ORDERS
    // Priority: exact barcode → patient name (partial).
    // Searches across all linked labs.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DentistOrderListResponse searchOrders(String query, int page, int size) {
        User currentUser = getAuthenticatedUser.execute();
        log.info("Searching orders for dentist: {} — query: '{}', page: {}, size: {}",
                currentUser.getUsername(), query, page, size);

        List<Doctor> doctors = doctorRepository.findByUser(currentUser);
        if (doctors.isEmpty()) {
            return emptyListResponse(page);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Priority 1: Exact barcode match
        Optional<Order> barcodeMatch = orderRepository.findByBarcodeIdAndDoctorIn(query, doctors);
        if (barcodeMatch.isPresent()) {
            log.info("Exact barcode match found for query: '{}' for dentist: {}", query, currentUser.getUsername());
            return DentistOrderListResponse.builder()
                    .orders(List.of(toDentistOrderResponse(barcodeMatch.get())))
                    .currentPage(0)
                    .totalPages(1)
                    .totalElements(1)
                    .build();
        }

        // Priority 2: Patient name search
        Page<Order> orderPage = orderRepository
                .findAllByPatientNameContainingIgnoreCaseAndDoctorIn(query, doctors, pageable);

        log.info("{} match — {} result(s) for query: '{}' for dentist: {}",
                orderPage.hasContent() ? "Patient name" : "No",
                orderPage.getTotalElements(), query, currentUser.getUsername());

        return toDentistOrderListResponse(orderPage);
    }

    // ─────────────────────────────────────────────────────────
    // GET ORDER BY ID
    // Ensures the order belongs to this dentist.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DentistOrderResponse getOrderById(UUID orderId) {
        User currentUser = getAuthenticatedUser.execute();
        log.info("Fetching order {} for dentist: {}", orderId, currentUser.getUsername());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        List<Doctor> doctors = doctorRepository.findByUser(currentUser);
        Set<UUID> doctorIds = doctors.stream().map(Doctor::getId).collect(Collectors.toSet());

        if (order.getDoctor() == null || !doctorIds.contains(order.getDoctor().getId())) {
            log.warn("Dentist {} attempted to access order {} which does not belong to them",
                    currentUser.getUsername(), orderId);
            throw new OperationNotPermittedException("This order does not belong to you.");
        }

        return toDentistOrderResponse(order);
    }

    // ─────────────────────────────────────────────────────────
    // GET OVERDUE ORDERS
    // Non-delivered orders where dueDate < today (IST).
    // Sorted by dueDate ascending (most overdue first).
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DentistOrderListResponse getOverdueOrders(int page, int size) {
        User currentUser = getAuthenticatedUser.execute();
        log.info("Fetching overdue orders for dentist: {} — page: {}, size: {}",
                currentUser.getUsername(), page, size);

        List<Doctor> doctors = doctorRepository.findByUser(currentUser);
        if (doctors.isEmpty()) {
            return emptyListResponse(page);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("dueDate").ascending());

        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.ORDER_CREATED,
                OrderStatus.IN_PROGRESS,
                OrderStatus.READY
        );

        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDateTime startOfDayIST = LocalDateTime.now(istZone).toLocalDate().atStartOfDay();

        log.info("Fetching overdue orders before: {}", startOfDayIST);

        Page<Order> orderPage = orderRepository.findOverdueOrdersByDoctorIn(
                activeStatuses, startOfDayIST, doctors, pageable);

        log.info("Found {} overdue order(s) for dentist: {}", orderPage.getTotalElements(), currentUser.getUsername());
        return toDentistOrderListResponse(orderPage);
    }

    // ─────────────────────────────────────────────────────────
    // GET ALL ORDERS BY LAB ID
    // Returns paginated orders for a specific lab this dentist
    // is linked to. Latest first.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DentistOrderListResponse getAllOrdersByLabId(UUID labId, OrderStatus status, int page, int size) {
        User currentUser = getAuthenticatedUser.execute();
        log.info("Fetching orders for dentist: {} — labId: {}, status: {}, page: {}, size: {}",
                currentUser.getUsername(), labId, status, page, size);

        Doctor doctor = doctorRepository.findByUserAndLabId(currentUser, labId)
                .orElseThrow(() -> new OperationNotPermittedException(
                        "You are not linked to this lab."
                ));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Order> orderPage = status != null
                ? orderRepository.findAllByDoctorAndCurrentStatus(doctor, status, pageable)
                : orderRepository.findAllByDoctor(doctor, pageable);

        log.info("Found {} order(s) in lab {} for dentist: {}",
                orderPage.getTotalElements(), labId, currentUser.getUsername());

        return toDentistOrderListResponse(orderPage);
    }

    // ─────────────────────────────────────────────────────────
    // GET ANALYTICS
    // Returns counts per status, overdue count, and total
    // for all orders belonging to this dentist's linked labs.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DentistAnalyticsResponse getAnalytics() {
        User currentUser = getAuthenticatedUser.execute();
        log.info("Fetching analytics for dentist: {}", currentUser.getUsername());

        List<Doctor> doctors = doctorRepository.findByUser(currentUser);
        if (doctors.isEmpty()) {
            log.info("Dentist {} has no linked labs — returning zero counts", currentUser.getUsername());
            return DentistAnalyticsResponse.builder()
                    .orderCreatedCount(0)
                    .inProgressCount(0)
                    .readyCount(0)
                    .deliveredCount(0)
                    .overdueCount(0)
                    .totalOrders(0)
                    .build();
        }

        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDateTime startOfDayIST = LocalDateTime.now(istZone).toLocalDate().atStartOfDay();

        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.ORDER_CREATED,
                OrderStatus.IN_PROGRESS,
                OrderStatus.READY
        );

        long orderCreatedCount = orderRepository.countByDoctorInAndCurrentStatus(doctors, OrderStatus.ORDER_CREATED);
        long inProgressCount   = orderRepository.countByDoctorInAndCurrentStatus(doctors, OrderStatus.IN_PROGRESS);
        long readyCount        = orderRepository.countByDoctorInAndCurrentStatus(doctors, OrderStatus.READY);
        long deliveredCount    = orderRepository.countByDoctorInAndCurrentStatus(doctors, OrderStatus.DELIVERED);
        long overdueCount      = orderRepository.countOverdueOrdersByDoctorIn(activeStatuses, startOfDayIST, doctors);
        long totalOrders       = orderRepository.countByDoctorIn(doctors);

        log.info("Analytics for dentist {}: created={}, inProgress={}, ready={}, delivered={}, overdue={}, total={}",
                currentUser.getUsername(), orderCreatedCount, inProgressCount, readyCount, deliveredCount, overdueCount, totalOrders);

        return DentistAnalyticsResponse.builder()
                .orderCreatedCount(orderCreatedCount)
                .inProgressCount(inProgressCount)
                .readyCount(readyCount)
                .deliveredCount(deliveredCount)
                .overdueCount(overdueCount)
                .totalOrders(totalOrders)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────

    private DentistOrderResponse toDentistOrderResponse(Order order) {
        return DentistOrderResponse.builder()
                .id(order.getId())
                .barcodeId(order.getBarcodeId())
                .caseNumber(order.getCaseNumber())
                .boxNumber(order.getBoxNumber())
                .dueDate(order.getDueDate())
                .deliverySchedule(order.getDeliverySchedule())
                .orderType(order.getOrderType())
                .patientName(order.getPatientName())
                .teeth(order.getTeeth())
                .shade(order.getShade())
                .materials(order.getMaterials())
                .imageUrl(order.getImageUrl())
                .currentStatus(order.getCurrentStatus())
                .currentStage(order.getCurrentStage())
                .labName(order.getDoctor() != null ? order.getDoctor().getLab().getName() : null)
                .createdAt(order.getCreatedAt())
                .deliveredAt(order.getDeliveredAt())
                .isEdited(order.isEdited())
                .build();
    }

    private DentistOrderListResponse toDentistOrderListResponse(Page<Order> orderPage) {
        List<DentistOrderResponse> responses = orderPage.getContent()
                .stream()
                .map(this::toDentistOrderResponse)
                .collect(Collectors.toList());

        return DentistOrderListResponse.builder()
                .orders(responses)
                .currentPage(orderPage.getNumber())
                .totalPages(orderPage.getTotalPages())
                .totalElements(orderPage.getTotalElements())
                .build();
    }

    private DentistOrderListResponse emptyListResponse(int page) {
        return DentistOrderListResponse.builder()
                .orders(List.of())
                .currentPage(page)
                .totalPages(0)
                .totalElements(0)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // GET DAILY ORDER COUNTS — last 30 days (IST, inclusive)
    // Counts across all labs the dentist is linked to.
    // Days with no orders have count 0.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DailyOrderCountResponse> getDailyOrderCounts() {
        User currentUser = getAuthenticatedUser.execute();

        List<Doctor> doctors = doctorRepository.findByUser(currentUser);
        if (doctors.isEmpty()) {
            return buildEmptyDailyCounts();
        }

        List<UUID> doctorIds = doctors.stream()
                .map(Doctor::getId)
                .collect(Collectors.toList());

        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(istZone);
        LocalDate startDate = today.minusDays(29); // 30 days including today

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = today.plusDays(1).atStartOfDay(); // exclusive upper bound

        log.info("Fetching daily order counts for dentist: {} ({} doctor record(s)) from {} to {}",
                currentUser.getUsername(), doctorIds.size(), startDate, today);

        List<Object[]> rows = orderRepository.findDailyOrderCountsByDoctorIds(
                doctorIds, startDateTime, endDateTime);

        Map<LocalDate, Long> countByDate = rows.stream()
                .collect(Collectors.toMap(
                        row -> LocalDate.parse(row[0].toString()),
                        row -> ((Number) row[1]).longValue()
                ));

        List<DailyOrderCountResponse> result = new ArrayList<>(30);
        for (int i = 0; i < 30; i++) {
            LocalDate date = startDate.plusDays(i);
            result.add(DailyOrderCountResponse.builder()
                    .date(date)
                    .count(countByDate.getOrDefault(date, 0L))
                    .build());
        }

        return result;
    }

    private List<DailyOrderCountResponse> buildEmptyDailyCounts() {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDate startDate = LocalDate.now(istZone).minusDays(29);
        List<DailyOrderCountResponse> result = new ArrayList<>(30);
        for (int i = 0; i < 30; i++) {
            result.add(DailyOrderCountResponse.builder()
                    .date(startDate.plusDays(i))
                    .count(0L)
                    .build());
        }
        return result;
    }
}
