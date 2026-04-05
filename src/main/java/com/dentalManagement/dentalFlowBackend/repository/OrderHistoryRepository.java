package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}