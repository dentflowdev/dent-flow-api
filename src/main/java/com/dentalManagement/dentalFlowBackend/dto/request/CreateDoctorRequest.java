package com.dentalManagement.dentalFlowBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


// CreateDoctorRequest.java
@Data
public class CreateDoctorRequest {

    @NotBlank(message = "Doctor name is required")
    private String doctorName;

    @NotBlank(message = "Location is required")
    private String location;

    private String email;
}
