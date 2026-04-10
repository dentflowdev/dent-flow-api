package com.dentalManagement.dentalFlowBackend.dto.response;

import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DentistOrderResponse {
    private UUID id;
    private String barcodeId;
    private String caseNumber;
    private String boxNumber;
    private LocalDateTime dueDate;
    private String deliverySchedule;
    private String orderType;
    private String patientName;
    private List<String> teeth;
    private List<String> shade;
    private List<String> materials;
    private String imageUrl;
    private OrderStatus currentStatus;
    private String currentStage;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private boolean isEdited;
}
