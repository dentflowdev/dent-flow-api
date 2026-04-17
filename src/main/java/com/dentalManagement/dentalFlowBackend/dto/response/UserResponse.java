package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String mobileNumber;
    private Set<String> roles;
    private LocalDateTime createdAt;
    private Set<UUID> labIds;
}
