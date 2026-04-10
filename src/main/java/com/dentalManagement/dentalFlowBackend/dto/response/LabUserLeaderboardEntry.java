package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LabUserLeaderboardEntry {
    private String firstName;
    private String lastName;
    private long orderCount;
    private long stageCount;
}
