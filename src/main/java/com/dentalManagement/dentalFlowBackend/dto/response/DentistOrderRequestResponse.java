package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DentistOrderRequestResponse {

    private UUID id;
    private String patientName;
    private LocalDateTime dueDate;

    private DoctorInfo doctor;

    private List<String> teeth;
    private List<String> shade;
    private List<String> materials;
    private String instructions;
    private String imageUrl;

    private LabInfo lab;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class DoctorInfo {
        private UUID doctorId;
        private String doctorName;
    }

    @Data
    @Builder
    public static class LabInfo {
        private UUID labId;
        private String labName;
    }
}
