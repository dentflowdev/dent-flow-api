package com.dentalManagement.dentalFlowBackend.controller;


import com.dentalManagement.dentalFlowBackend.dto.request.DoctorRegisterRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.LabRegistrationRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.LoginRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.RegisterRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.AuthResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.LabRegistrationResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.UserResponse;
import com.dentalManagement.dentalFlowBackend.service.AuthService;
import com.dentalManagement.dentalFlowBackend.service.LabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LabService labService;

    @PostMapping("/user/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/dentist/register")
    public ResponseEntity<UserResponse> registerDoctor(@Valid @RequestBody DoctorRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerDoctor(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestParam String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/labs/register")
    public ResponseEntity<LabRegistrationResponse> registerLab(
            @Valid @RequestBody LabRegistrationRequest request) {

        LabRegistrationResponse response = labService.registerLab(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
