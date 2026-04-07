package com.dentalManagement.dentalFlowBackend.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateOrderRequest {

    private String boxNumber;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime dueDate;

    private String deliverySchedule;
    private String orderType;
    private String patientName;
    private UUID doctorId;
    private List<String> teeth;
    private List<String> shade;
    private List<String> materials;
    private String instructions;
}