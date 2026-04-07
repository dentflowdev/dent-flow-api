package com.dentalManagement.dentalFlowBackend.dto.response;

import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─────────────────────────────────────────────────────────────
// Order snapshot — no history, no user details
// ─────────────────────────────────────────────────────────────
@Data
@Builder
public class OrderResponse {

    private UUID id;
    private String barcodeId;

    private CaseDetails caseDetails;
    private PatientDetails patientDetails;
    private ClinicalDetails clinicalDetails;
    private AdditionalDetails additionalDetails;

    private OrderStatus currentStatus;
    private String currentStage;

    private String imageUrl;

    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private boolean isEdited;

    // ✅ NEW: Workflow stages included in response
    // Populated only when fetching single order details
    private List<WorkflowStage> workflowStages;

    // ── Nested response objects ───────────────────────────────

    @Data
    @Builder
    public static class CaseDetails {
        private String caseNumber;
        private String boxNumber;
        private LocalDateTime dueDate;
        private String deliverySchedule;
        private String orderType;
    }

    @Data
    @Builder
    public static class PatientDetails {
        private String name;
    }

    @Data
    @Builder
    public static class DoctorInfo {
        private UUID doctorId;
        private String doctorName;
    }

    @Data
    @Builder
    public static class ClinicalDetails {
        private DoctorInfo doctor;
        private List<String> teeth;
        private List<String> shade;
        private List<String> materials;
    }

    @Data
    @Builder
    public static class AdditionalDetails {
        private String instructions;
    }

    // ── Workflow Stage ────────────────────────────────────────
    @Data
    @Builder
    public static class WorkflowStage {
        private String stageLabel;      // e.g., "Pouring", "Scanning"
        private String stageName;       // e.g., "POURING", "SCANNING"
        private Integer stageOrder;     // 1, 2, 3, ...
    }
}