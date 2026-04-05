package com.dentalManagement.dentalFlowBackend.dto.request;


import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
// AssignRoleRequest.java
@Data
public class AssignRoleRequest {

    @NotNull(message = "Role is required")
    private RoleName roleName;
}
