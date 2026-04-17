package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DentistLabResponse {
    private UUID id;
    private String name;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String mobileNumber;
    private String email;
    private long orderCount;
}
