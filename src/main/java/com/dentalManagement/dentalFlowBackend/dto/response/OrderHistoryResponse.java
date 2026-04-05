package com.dentalManagement.dentalFlowBackend.dto.response;

import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UPDATED for Dynamic Workflow System
 *
 * Changes:
 * - previousStage: OrderStage enum → String (stages from database)
 * - newStage: OrderStage enum → String (stages from database)
 * - All other fields unchanged
 *
 * Example response:
 * {
 *   "orderId": "123e4567-e89b-12d3-a456-426614174000",
 *   "history": [
 *     {
 *       "changedByName": "John Doe",
 *       "roleAtTime": "MARKETING_EXECUTIVE",
 *       "previousStatus": null,
 *       "previousStage": null,
 *       "newStatus": "ORDER_CREATED",
 *       "newStage": null,
 *       "remarks": "Order created",
 *       "changedAt": "2024-03-29T10:30:00"
 *     },
 *     {
 *       "changedByName": "Jane Smith",
 *       "roleAtTime": "TECHNICIAN",
 *       "previousStatus": "ORDER_CREATED",
 *       "previousStage": null,
 *       "newStatus": "IN_PROGRESS",
 *       "newStage": "POURING",  ← Now String!
 *       "remarks": "Stage updated to POURING",
 *       "changedAt": "2024-03-29T11:00:00"
 *     }
 *   ]
 * }
 */
@Data
@Builder
public class OrderHistoryResponse {

    private UUID orderId;
    private List<HistoryEntry> history;

    @Data
    @Builder
    public static class HistoryEntry {
        private String changedByName;        // first + last name
        private String roleAtTime;           // role at time of change

        private OrderStatus previousStatus;  // ✅ Keep as enum
        private String previousStage;        // ✅ CHANGED: String (was OrderStage enum)

        private OrderStatus newStatus;       // ✅ Keep as enum
        private String newStage;             // ✅ CHANGED: String (was OrderStage enum)

        private String remarks;              // description of change
        private LocalDateTime changedAt;     // timestamp
    }
}