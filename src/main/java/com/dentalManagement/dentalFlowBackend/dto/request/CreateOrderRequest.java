package com.dentalManagement.dentalFlowBackend.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

// ─────────────────────────────────────────────────────────────
// Top-level request — matches exactly what frontend sends
// ─────────────────────────────────────────────────────────────
@Data
public class CreateOrderRequest {

    @NotBlank(message = "Barcode ID is required")
    private String barcodeId;

    @NotNull(message = "Case details are required")
    private CaseDetails caseDetails;

    @NotNull(message = "Patient details are required")
    private PatientDetails patientDetails;

    @NotNull(message = "Clinical details are required")
    private ClinicalDetails clinicalDetails;

    private AdditionalDetails additionalDetails;

    // ── Nested DTOs ───────────────────────────────────────────

    @Data
    public static class CaseDetails {
        @NotBlank(message = "Case number is required")
        private String caseNumber;

        private String boxNumber;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private LocalDateTime dueDate;

        private String deliverySchedule;
        private String orderType;
    }

    @Data
    public static class PatientDetails {
        @NotBlank(message = "Patient name is required")
        private String name;
    }

    @Data
    public static class ClinicalDetails {
        @NotBlank(message = "Doctor name is required")
        private String doctor;

        private List<String> teeth;
        private List<String> shade;
        private List<String> materials;
    }

    @Data
    public static class AdditionalDetails {
        private String instructions;
    }
}
