package com.dentalManagement.dentalFlowBackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRegisterRequest {

    @NotBlank
    private String username;

    private String firstName;
    private String lastName;

    // Required — used to find matching Doctor records across labs and link them.
    @NotBlank(message = "Email is required for doctor registration")
    @Email
    private String email;

    @Pattern(regexp = "^[0-9]{10}$")
    private String mobileNumber;

    @NotBlank
    @Size(min = 8)
    private String password;
}
