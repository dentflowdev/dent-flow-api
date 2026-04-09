package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DentistOrderListResponse {
    private List<DentistOrderResponse> orders;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}
