package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.response.DailyOrderCountResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistAnalyticsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.DoctorOrderCountResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // ─────────────────────────────────────────────────────────
    // GET DAILY ORDER COUNTS — last 30 days (IST, inclusive)
    // Returns one entry per day. Days with no orders have count 0.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DailyOrderCountResponse> getDailyOrderCounts() {
        User currentUser = getAuthenticatedUser.execute();
        Lab currentLab = currentUser.getPrimaryLab();

        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(istZone);
        LocalDate startDate = today.minusDays(29); // 30 days including today

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = today.plusDays(1).atStartOfDay(); // exclusive upper bound

        log.info("Fetching daily order counts for lab: {} from {} to {}", currentLab.getId(), startDate, today);

        List<Object[]> rows = orderRepository.findDailyOrderCountsByLab(
                currentLab.getId(), startDateTime, endDateTime);

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

    // ─────────────────────────────────────────────────────────
    // GET DOCTOR ORDER COUNTS — current month (IST)
    // All doctors in the lab, with order count from 1st of the
    // current month up to and including today. Sorted high→low.
    // Doctors with 0 orders in the period are still included.
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DoctorOrderCountResponse> getDoctorOrderCountsCurrentMonth() {
        User currentUser = getAuthenticatedUser.execute();
        Lab currentLab = currentUser.getPrimaryLab();

        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(istZone);
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay(); // exclusive

        log.info("Fetching doctor order counts for lab: {} from {} to {}", currentLab.getId(), startOfMonth.toLocalDate(), today);

        List<Object[]> rows = orderRepository.findOrderCountPerDoctorInPeriod(
                currentLab.getId(), startOfMonth, endOfToday);

        return rows.stream()
                .map(row -> DoctorOrderCountResponse.builder()
                        .doctorName((String) row[0])
                        .location((String) row[1])
                        .email((String) row[2])
                        .orderCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());
    }
}
