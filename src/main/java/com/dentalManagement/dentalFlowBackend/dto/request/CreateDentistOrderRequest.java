package com.dentalManagement.dentalFlowBackend.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreateDentistOrderRequest {

    @NotNull(message = "Lab ID is required")
    private UUID labId;

    @NotBlank(message = "Patient name is required")
    private String patientName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime dueDate;

    private List<String> teeth;
    private List<String> shade;
    private List<String> materials;
    private String instructions;
}
