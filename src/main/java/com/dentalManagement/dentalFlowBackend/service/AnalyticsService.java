package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.response.DentistAnalyticsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.StageCountDtoResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.objectMapper.ResponseMapper;
import com.dentalManagement.dentalFlowBackend.repository.OrderRepository;
import com.dentalManagement.dentalFlowBackend.util.GetAuthenticatedUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private final OrderRepository orderRepository;
    private final ResponseMapper responseMapper;
    private final GetAuthenticatedUser getAuthenticatedUser;

    /**
     * Get count of all stages in IN_PROGRESS status with stage labels
     * @return List of StageCountDto with stage names, labels, and their counts
     */
    public List<StageCountDtoResponse> getStageCountsForInProgress() {
        log.info("Fetching stage counts for IN_PROGRESS orders");

        List<Object[]> results = orderRepository.findStageCountByStatus(OrderStatus.IN_PROGRESS);

        return responseMapper.mapResultsToStageCountDto(results);
    }

    // ─────────────────────────────────────────────────────────
    // GET ORDER SUMMARY COUNTS
    // Returns total orders, per-status counts, and overdue count
    // scoped to the authenticated user's primary lab.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DentistAnalyticsResponse getOrderSummaryCounts() {
        User currentUser = getAuthenticatedUser.execute();
        Lab currentLab = currentUser.getPrimaryLab();

        log.info("Fetching order summary counts for lab: {}", currentLab.getId());

        long total        = orderRepository.countByLab(currentLab);
        long orderCreated = orderRepository.countByStatusAndLab(OrderStatus.ORDER_CREATED, currentLab);
        long inProgress   = orderRepository.countByStatusAndLab(OrderStatus.IN_PROGRESS, currentLab);
        long ready        = orderRepository.countByStatusAndLab(OrderStatus.READY, currentLab);
        long delivered    = orderRepository.countByStatusAndLab(OrderStatus.DELIVERED, currentLab);

        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.ORDER_CREATED,
                OrderStatus.IN_PROGRESS,
                OrderStatus.READY
        );
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDateTime startOfDayIST = LocalDateTime.now(istZone).toLocalDate().atStartOfDay();

        long overdue = orderRepository.countOverdueOrdersByLab(activeStatuses, startOfDayIST, currentLab);

        log.info("Order summary — total: {}, orderCreated: {}, inProgress: {}, ready: {}, delivered: {}, overdue: {}",
                total, orderCreated, inProgress, ready, delivered, overdue);

        return DentistAnalyticsResponse.builder()
                .totalOrders(total)
                .orderCreatedCount(orderCreated)
                .inProgressCount(inProgress)
                .readyCount(ready)
                .deliveredCount(delivered)
                .overdueCount(overdue)
                .build();
    }
}
