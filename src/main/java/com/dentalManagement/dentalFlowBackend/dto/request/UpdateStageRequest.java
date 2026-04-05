package com.dentalManagement.dentalFlowBackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// ── Used by Technician to advance the order to next stage ────
@Data
public class UpdateStageRequest {

    @NotNull(message = "New stage is required")
    private String newStage;

    private String remarks;
}