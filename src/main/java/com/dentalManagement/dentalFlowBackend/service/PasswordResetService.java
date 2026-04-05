package com.dentalManagement.dentalFlowBackend.service;


import com.dentalManagement.dentalFlowBackend.model.ResetTokenEntity;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.repository.ResetTokenRepository;
import com.dentalManagement.dentalFlowBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PasswordResetService {

    private final ResetTokenRepository resetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${password-reset.token-expiry-minutes:60}")
    private int resetTokenExpiryMinutes;

    /**
     * Generate a one-time reset token after OTP is verified
     * Token is stored in database with expiry time
     */
    public String generateResetToken(String email) {
        String resetToken = UUID.randomUUID().toString();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes);

        // Create and save reset token entity
        ResetTokenEntity resetTokenEntity = ResetTokenEntity.builder()
                .resetToken(resetToken)
                .email(email)
                .expiryTime(expiryTime)
                .used(false)
                .build();

        resetTokenRepository.save(resetTokenEntity);
        log.info("Reset token generated for email: [{}], expires at: [{}]", email, expiryTime);

        return resetToken;
    }

    /**
     * Validate reset token and return the associated email
     */
    public String validateResetToken(String resetToken) {
        Optional<ResetTokenEntity> tokenEntityOpt =
                resetTokenRepository.findByResetTokenAndUsedFalse(resetToken);

        if (tokenEntityOpt.isEmpty()) {
            log.warn("Reset token not found or already used");
            throw new RuntimeException("Reset token is invalid or already used. Please request a new OTP.");
        }

        ResetTokenEntity tokenEntity = tokenEntityOpt.get();

        // Check if token has expired
        if (tokenEntity.isExpired()) {
            log.warn("Reset token has expired");
            throw new RuntimeException("Reset token is expired. Please request a new OTP.");
        }

        log.info("Reset token validated for email: [{}]", tokenEntity.getEmail());
        return tokenEntity.getEmail();
    }

    /**
     * Reset password using valid reset token
     * Validates token, updates password in database, and marks token as used
     */
    public void resetPassword(String resetToken, String newPassword, String confirmPassword) {
        // Validate passwords match
        if (!newPassword.equals(confirmPassword)) {
            log.warn("Password confirmation mismatch for reset token");
            throw new RuntimeException("Passwords do not match.");
        }

        // Validate password strength
        if (newPassword.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long.");
        }

        // Validate token and get email
        String email = validateResetToken(resetToken);

        // Get user from database
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark reset token as used (for security - one-time use only)
        ResetTokenEntity tokenEntity = resetTokenRepository.findByResetToken(resetToken)
                .orElseThrow(() -> new RuntimeException("Reset token not found"));
        tokenEntity.setUsed(true);
        resetTokenRepository.save(tokenEntity);

        // Send password reset success email
        try {
            emailService.sendPasswordResetSuccessEmail(email);
            log.info("Password reset success email sent to: [{}]", email);
        } catch (Exception e) {
            log.error("Failed to send password reset success email to: [{}]. Error: [{}]",
                    email, e.getMessage());
            // Don't throw exception - password was already reset successfully
            // Email notification failure shouldn't block the password reset
        }

        log.info("Password reset successfully for email: [{}]", email);
    }

    /**
     * Verify reset token is still valid (without consuming it)
     */
    public boolean isResetTokenValid(String resetToken) {
        Optional<ResetTokenEntity> tokenEntityOpt =
                resetTokenRepository.findByResetTokenAndUsedFalse(resetToken);

        if (tokenEntityOpt.isEmpty()) {
            return false;
        }

        return !tokenEntityOpt.get().isExpired();
    }

    /**
     * Get remaining TTL for reset token in seconds
     */
    public Long getResetTokenTtl(String resetToken) {
        Optional<ResetTokenEntity> tokenEntityOpt =
                resetTokenRepository.findByResetToken(resetToken);

        if (tokenEntityOpt.isEmpty()) {
            return -1L;
        }

        ResetTokenEntity tokenEntity = tokenEntityOpt.get();
        if (tokenEntity.isExpired() || tokenEntity.isUsed()) {
            return 0L;
        }

        long secondsRemaining = java.time.temporal.ChronoUnit.SECONDS
                .between(LocalDateTime.now(), tokenEntity.getExpiryTime());
        return Math.max(0, secondsRemaining);
    }
}