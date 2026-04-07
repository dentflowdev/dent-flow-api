package com.dentalManagement.dentalFlowBackend.service;


import com.dentalManagement.dentalFlowBackend.dto.request.LoginRequest;
import com.dentalManagement.dentalFlowBackend.dto.request.RegisterRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.AuthResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.UserResponse;
import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.RefreshToken;
import com.dentalManagement.dentalFlowBackend.model.Role;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.objectMapper.ResponseMapper;
import com.dentalManagement.dentalFlowBackend.repository.LabRepository;
import com.dentalManagement.dentalFlowBackend.repository.RefreshTokenRepository;
import com.dentalManagement.dentalFlowBackend.repository.RoleRepository;
import com.dentalManagement.dentalFlowBackend.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ResponseMapper responseMapper;
    private final LabRepository labRepository;

    @Value("${jwt.refresh-token-expiry:7776000000}")
    private long refreshTokenExpiry;

    public UserResponse register(RegisterRequest request) {

        // Check if username already exists
        Optional<User> existingUser = userRepository.findByUsernameIgnoreCase(request.getUsername());

        if (existingUser.isPresent()) {
            User user = existingUser.get();

            // If user is INACTIVE (soft deleted), allow re-registration
            if (!user.isActive()) {
                log.info("Re-registering inactive user - username: [{}]", request.getUsername());
                return reactivateUser(user, request);
            }

            //  If user is ACTIVE, reject registration
            log.warn("Registration failed - username already taken by active user: [{}]", request.getUsername());
            throw new RuntimeException("Username already taken");
        }

        // New user registration
        Role defaultRole = roleRepository.findByRoleName(RoleName.ROLE_DEFAULT_USER)
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        Optional<Lab> labOptional = labRepository.findByLabCode(request.getLabCode());
        if (labOptional.isEmpty()) {
            log.error("Lab not found with lab code: {}", request.getLabCode());
            throw new RuntimeException("Invalid lab code: " + request.getLabCode());
        }
        Lab labFound = labOptional.get();

        User user = User.builder()
                .username(request.getUsername())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .mobileNumber(request.getMobileNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(defaultRole))
                .labs(new HashSet<>(Set.of(labFound)))
                .build();

        User saved = userRepository.save(user);
        log.info("User registered successfully - username: [{}], email: [{}]",
                saved.getUsername(), saved.getEmail());

        return responseMapper.toUserResponse(saved);
    }

    // NEW METHOD: Reactivate and update soft-deleted user
    public UserResponse reactivateUser(User user, RegisterRequest request) {
        try {
            // Get default role
            Role defaultRole = roleRepository.findByRoleName(RoleName.ROLE_DEFAULT_USER)
                    .orElseThrow(() -> new RuntimeException("Default role not found"));

            // ✅ Update basic fields
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setMobileNumber(request.getMobileNumber());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setActive(true);

            log.info("User reactivated value is set");

            // ✅ Clear ALL existing roles
            user.getRoles().clear();
            log.info("Cleared all existing roles for user: {}", user.getId());

            // ✅ Add only default role
            user.getRoles().add(defaultRole);
            log.info("Added ROLE_DEFAULT_USER to user: {}", user.getId());

            // ✅ Save with updated roles
            User updated = userRepository.saveAndFlush(user);

            log.info("User reactivated successfully - username: [{}], email: [{}], userId: [{}]",
                    updated.getUsername(), updated.getEmail(), updated.getId());

            return responseMapper.toUserResponse(updated);

        } catch (Exception e) {
            log.error("Error reactivating user: ", e);
            throw new RuntimeException("Failed to reactivate user: " + e.getMessage());
        }
    }


    public AuthResponse login(LoginRequest request) {
        // This throws BadCredentialsException automatically if wrong
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        UserDetails userDetails = (UserDetails) auth.getPrincipal();

        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = createAndSaveRefreshToken(userDetails);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow();
        log.info("Login successful for username: [{}]", request.getUsername());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accessTokenExpiresIn(3600)
                .user(responseMapper.toUserResponse(user))
                .build();
    }

    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found: [{}]", refreshTokenValue);
                    return new RuntimeException("Refresh token not found");
                });

        String username = storedToken.getUser().getUsername();

        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            log.warn("Refresh token expired and deleted for username: [{}]", username);
            throw new RuntimeException("Refresh token expired, please login again");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(
                storedToken.getUser().getUsername());

        String newAccessToken  = jwtService.generateAccessToken(userDetails);
        String newRefreshToken = createAndSaveRefreshToken(userDetails);
        log.info("Refresh token rotated successfully for username: [{}]", username);
        // Delete old token (rotation)
        refreshTokenRepository.delete(storedToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .accessTokenExpiresIn(3600)
                .build();
    }

    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresentOrElse(
                        token -> {
                            String username = token.getUser().getUsername();
                            refreshTokenRepository.delete(token);
                            log.info("Logout successful - refresh token deleted for username: [{}]", username);
                        },
                        () -> log.warn("Logout attempt with unrecognized or already-invalidated refresh token: [{}]", refreshTokenValue)
                );
    }

    private String createAndSaveRefreshToken(UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();

        // ✅ Delete ALL existing refresh tokens for this user
        List<RefreshToken> existingTokens = refreshTokenRepository.findAllByUserId(user.getId());
        if (!existingTokens.isEmpty()) {
            refreshTokenRepository.deleteAll(existingTokens);
            log.info("Deleted [{}] existing refresh token(s) for username: [{}]",
                    existingTokens.size(), user.getUsername());
        }

        String tokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiryDate(Instant.now().plusMillis(refreshTokenExpiry))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

}
