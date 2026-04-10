package com.dentalManagement.dentalFlowBackend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DentistAnalyticsResponse {

    @JsonProperty("New")
    private long orderCreatedCount;

    @JsonProperty("In Production")
    private long inProgressCount;

    @JsonProperty("Ready")
    private long readyCount;

    @JsonProperty("Delivered")
    private long deliveredCount;

    @JsonProperty("Overdue")
    private long overdueCount;

    @JsonProperty("Total")
    private long totalOrders;
}
