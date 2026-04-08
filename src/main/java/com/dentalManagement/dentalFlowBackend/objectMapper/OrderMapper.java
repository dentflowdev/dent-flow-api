package com.dentalManagement.dentalFlowBackend.objectMapper;

import com.dentalManagement.dentalFlowBackend.dto.response.OrderHistoryResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.OrderResponse;
import com.dentalManagement.dentalFlowBackend.model.Order;
import com.dentalManagement.dentalFlowBackend.model.OrderHistory;
import com.dentalManagement.dentalFlowBackend.model.LabWorkflowStage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UPDATED for Dynamic Workflow System
 *
 * Changes:
 * - toOrderResponse(): Maps currentStage as String (was OrderStage enum)
 * - toOrderResponseWithWorkflow(): NEW - includes complete workflow stages
 * - toOrderHistoryResponse(): Handles String stages (unchanged logic)
 */
@Component
public class OrderMapper {

    // ── Map Order entity → OrderResponse (WITHOUT workflow) ────
    // Used for: Create, List, Update operations (performance)
    public OrderResponse toOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .barcodeId(order.getBarcodeId())
                .caseDetails(OrderResponse.CaseDetails.builder()
                        .caseNumber(order.getCaseNumber())
                        .boxNumber(order.getBoxNumber())
                        .dueDate(order.getDueDate())
                        .deliverySchedule(order.getDeliverySchedule())
                        .orderType(order.getOrderType())
                        .build())
                .patientDetails(OrderResponse.PatientDetails.builder()
                        .name(order.getPatientName())
                        .build())
                .clinicalDetails(OrderResponse.ClinicalDetails.builder()
                        .doctor(order.getDoctor() != null
                                ? OrderResponse.DoctorInfo.builder()
                                        .doctorId(order.getDoctor().getId())
                                        .doctorName(order.getDoctor().getDoctorName())
                                        .build()
                                : null)
                        .teeth(order.getTeeth())
                        .shade(order.getShade())
                        .materials(order.getMaterials())
                        .build())
                .additionalDetails(OrderResponse.AdditionalDetails.builder()
                        .instructions(order.getInstructions())
                        .build())
                .currentStatus(order.getCurrentStatus())
                // ✅ Changed: String instead of enum
                .currentStage(order.getCurrentStage() != null ? order.getCurrentStage().toString() : null)
                .imageUrl(order.getImageUrl())
                .workflowStages(null)  // Not included in basic response
                .createdAt(order.getCreatedAt())
                .deliveredAt(order.getDeliveredAt())
                .isEdited(order.isEdited())
                .build();
    }

    // ── Map Order entity → OrderResponse (WITH workflow) ───────
    // Used for: GET /api/v1/orders/{orderId} (single order fetch)
    // Includes complete workflow stages for UI to display
    public OrderResponse toOrderResponseWithWorkflow(Order order) {
        // First, build base response
        OrderResponse response = toOrderResponse(order);

        // Then, add workflow stages if workflow exists
        if (order.getWorkflow() != null && !order.getWorkflow().getStages().isEmpty()) {
            List<OrderResponse.WorkflowStage> workflowStages = order.getWorkflow().getStages()
                    .stream()
                    .map(stage -> OrderResponse.WorkflowStage.builder()
                            .stageLabel(stage.getStageLabel())
                            .stageName(stage.getStageName())
                            .stageOrder(stage.getStageOrder())
                            .build())
                    .collect(Collectors.toList());

            response.setWorkflowStages(workflowStages);
        } else if (order.getWorkflow() == null) {
            // Old order with no workflow attached - use default workflow stages
            // This would require injecting OrderStateMachine or LabWorkflowRepository
            // For now, leaving it as null (optional enhancement)
            response.setWorkflowStages(null);
        }

        return response;
    }

    // ── Map list of OrderHistory → OrderHistoryResponse ───────
    // NO CHANGES - stages are now String, mapper handles automatically
    public OrderHistoryResponse toOrderHistoryResponse(UUID orderId, List<OrderHistory> historyList) {
        List<OrderHistoryResponse.HistoryEntry> entries = historyList.stream()
                .map(h -> OrderHistoryResponse.HistoryEntry.builder()
                        .changedByName(h.getChangedBy().getFirstName() + " " + h.getChangedBy().getLastName())
                        .roleAtTime(h.getRoleAtTime())
                        .previousStatus(h.getPreviousStatus())
                        .previousStage(h.getPreviousStage())
                        .newStatus(h.getNewStatus())
                        .newStage(h.getNewStage())
                        .remarks(h.getRemarks())
                        .changedAt(h.getChangedAt())
                        .build())
                .collect(Collectors.toList());

        return OrderHistoryResponse.builder()
                .orderId(orderId)
                .history(entries)
                .build();
    }
}