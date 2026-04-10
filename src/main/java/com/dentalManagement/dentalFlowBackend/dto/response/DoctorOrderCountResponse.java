package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DoctorOrderCountResponse {
    private String doctorName;
    private String location;
    private String email;
    private long orderCount;
}
