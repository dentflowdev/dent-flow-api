package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class LabUserLeaderboardResponse {
    private String role;
    private List<LabUserLeaderboardEntry> leaderboard;
}
