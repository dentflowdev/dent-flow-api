package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LabRegistrationResponse {
    private UUID labId;
    private String name;
    private String email;
    // Return the generated lab code to the registrant so they can share it with employees
    private String labCode;
    private String adminUsername;
    private String message;
}
