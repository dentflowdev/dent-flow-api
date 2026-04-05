package com.dentalManagement.dentalFlowBackend.controller;


import com.dentalManagement.dentalFlowBackend.dto.request.AssignRoleRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.UserResponse;
import com.dentalManagement.dentalFlowBackend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/getallusers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUser(){
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PatchMapping("/user/{userId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID userId) {
        adminService.deactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/getuser/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(adminService.getUserByUserName(username));
    }

    @DeleteMapping("/deleteuser/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUserById(@PathVariable UUID userId) {
        adminService.deleteUserById(userId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────
    // PATCH /api/v1/users/{userId}/role
    // Admin assigns or replaces a role for a user.
    // ─────────────────────────────────────────────────────────
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID userId,
            @RequestBody @Valid AssignRoleRequest request) {

        log.info("PATCH /api/v1/users/{}/role — role: {}", userId, request.getRoleName());
        adminService.assignRole(userId, request);
        return ResponseEntity.noContent().build();
    }

}
