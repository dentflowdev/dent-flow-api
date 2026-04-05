package com.dentalManagement.dentalFlowBackend.objectMapper;


import com.dentalManagement.dentalFlowBackend.dto.response.StageCountDtoResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.UserResponse;
import com.dentalManagement.dentalFlowBackend.model.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResponseMapper {
    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .roles(user.getRoles().stream()
                        .map(r -> r.getRoleName().name())
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .labId(user.getLab() != null ? user.getLab().getId().toString() : null)
                .build();
    }

    /**
     * Helper method to map Object[] results to StageCountDto
     * Results should be in order: [stageName, stageLabel, count]
     */
    public List<StageCountDtoResponse> mapResultsToStageCountDto(List<Object[]> results) {
        return results.stream()
                .map(result -> {
                    String stageName = (String) result[0];
                    String stageLabel = (String) result[1];  // Can be null if no matching LabWorkflowStage
                    Long count = ((Number) result[2]).longValue();

                    // If stageLabel is null, use stageName as fallback
                    String displayLabel = stageLabel != null ? stageLabel : stageName;

                    return StageCountDtoResponse.builder()
                            .stageName(stageName)
                            .stageLabel(displayLabel)
                            .count(count)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
