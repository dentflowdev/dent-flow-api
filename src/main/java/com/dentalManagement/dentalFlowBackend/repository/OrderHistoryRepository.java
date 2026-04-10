package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {

    // Full audit trail for an order, oldest first
    List<OrderHistory> findAllByOrderIdOrderByChangedAtAsc(UUID orderId);

    // Delete all history records for an order
    int deleteAllByOrderId(UUID orderId);

    /**
     * Delete all history records EXCEPT those with newStatus = ORDER_CREATED.
     *
     * Used when a material change results in a workflow change.
     * Keeps the initial ORDER_CREATED history entry for audit trail,
     * but removes all intermediate stage progression records.
     *
     * @param orderId The order ID whose history should be cleaned
     * @return Number of records deleted
     */
    @Modifying
    @Query("DELETE FROM OrderHistory oh " +
            "WHERE oh.order.id = :orderId " +
            "AND oh.newStatus != 'ORDER_CREATED'")
    int deleteAllByOrderIdExceptOrderCreated(@Param("orderId") UUID orderId);

    // ── Lab user leaderboard ──────────────────────────────────────
    // For each user+role combination: count distinct orders touched
    // and total history entries (stage actions) within the period,
    // scoped to orders belonging to the given lab.
    // Sorted by role then order_count DESC.
    @Query(value = """
            SELECT
                u.first_name,
                u.last_name,
                oh.role_at_time,
                COUNT(DISTINCT oh.order_id) AS order_count,
                COUNT(oh.id)                AS stage_count
            FROM order_history oh
            JOIN orders  o  ON oh.order_id   = o.id
            JOIN users   u  ON oh.changed_by = u.id
            JOIN user_labs ul ON o.created_by = ul.user_id
            WHERE ul.lab_id      = :labId
              AND oh.changed_at >= :startDate
              AND oh.changed_at  < :endDate
            GROUP BY u.id, u.first_name, u.last_name, oh.role_at_time
            ORDER BY oh.role_at_time, order_count DESC
            """, nativeQuery = true)
    List<Object[]> findLabUserLeaderboard(
            @Param("labId") UUID labId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}