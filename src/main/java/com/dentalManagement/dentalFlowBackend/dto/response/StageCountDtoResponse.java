package com.dentalManagement.dentalFlowBackend.dto.response;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageCountDtoResponse {
    private String stageName;      // e.g. "POURING"
    private String stageLabel;     // e.g. "Pouring & Trimming"
    private Long count;
}
