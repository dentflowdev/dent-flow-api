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
@NoArgsConstructor      // ← Jackson needs this
@AllArgsConstructor     // ← @Builder needs this
public class RegisterRequest {
    @NotBlank
    private String username;
    private String firstName;
    private String lastName;
    @Email
    private String email;
    @Pattern(regexp = "^[0-9]{10}$") private String mobileNumber;
    @NotBlank @Size(min = 8) private String password;
    @NotBlank
    private String labCode;
}
