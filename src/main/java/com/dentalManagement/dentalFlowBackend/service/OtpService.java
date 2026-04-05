package com.dentalManagement.dentalFlowBackend.service;


import com.dentalManagement.dentalFlowBackend.model.OtpEntity;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.repository.OtpRepository;
import com.dentalManagement.dentalFlowBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OtpService {

    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final OtpAttemptService otpAttemptService;
    private final Random random = new Random();

    @Value("${otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.max-attempts:3}")
    private int maxAttempts;

    /**
     * Generate OTP and send to email
     * OTP is stored in database with expiry time
     */
    public void generateAndSendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        String otp = generateOtp();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(otpExpiryMinutes);

        // Find existing OTP and update, or create new
        Optional<OtpEntity> existingOtp = otpRepository.findByEmail(email);

        OtpEntity otpEntity;
        if (existingOtp.isPresent()) {
            otpEntity = existingOtp.get();
            otpEntity.setOtp(otp);
            otpEntity.setExpiryTime(expiryTime);
            otpEntity.setFailedAttempts(0);
        } else {
            otpEntity = OtpEntity.builder()
                    .email(email)
                    .otp(otp)
                    .expiryTime(expiryTime)
                    .failedAttempts(0)
                    .build();
        }

        otpRepository.save(otpEntity);

        log.info("OTP generated and stored in database for email: [{}], expires at: [{}]",
                email, expiryTime);

        // Send OTP via email
        try {
            emailService.sendOtpEmail(email, otp, otpExpiryMinutes);
            log.info("OTP email sent successfully to: [{}]", email);
        } catch (Exception e) {
            log.error("OTP Service : Failed to send OTP email to: [{}]", email, e);
            throw new RuntimeException("Failed to send OTP email. Please try again.");
        }
    }

    /**
     * Verify OTP provided by user
     * Returns true if OTP matches and is within expiry time
     */
    public boolean verifyOtp(String email, String providedOtp) {
        Optional<OtpEntity> otpEntityOpt = otpRepository.findByEmail(email);

        if (otpEntityOpt.isEmpty()) {
            log.warn("OTP not found for email: [{}]", email);
            throw new RuntimeException("OTP not found. Please request a new one.");
        }

        OtpEntity otpEntity = otpEntityOpt.get();

        // Check if OTP has expired
        if (otpEntity.isExpired()) {
            log.warn("OTP expired for email: [{}]", email);
            otpRepository.delete(otpEntity);
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        // Check if max attempts exceeded
        if (otpEntity.isMaxAttemptsExceeded(maxAttempts)) {
            log.warn("Max OTP attempts exceeded for email: [{}]", email);
            otpRepository.delete(otpEntity);
            throw new RuntimeException("Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        // Verify OTP
        if (otpEntity.getOtp().equals(providedOtp)) {
            // Delete OTP from database immediately after successful verification
            otpRepository.delete(otpEntity);
            log.info("OTP verified successfully for email: [{}]", email);
            return true;
        } else {
            // Increment and persist BEFORE throwing
            otpAttemptService.incrementFailedAttempt(email);
            throw new RuntimeException("Invalid OTP. Please try again.");
        }
    }

    /**
     * Resend OTP (delete old and generate new)
     */
    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        // Delete old OTP from database
        otpRepository.findByEmail(email).ifPresent(otpRepository::delete);

        log.info("Old OTP deleted for email: [{}]", email);

        // Generate and send new OTP
        generateAndSendOtp(email);
    }

    /**
     * Check if OTP is still valid (without consuming it)
     */
    public boolean isOtpValid(String email) {
        Optional<OtpEntity> otpEntityOpt = otpRepository.findByEmail(email);
        if (otpEntityOpt.isEmpty()) {
            return false;
        }
        return !otpEntityOpt.get().isExpired();
    }

    /**
     * Get remaining TTL for OTP in seconds (for UI countdown)
     */
    public Long getOtpTtl(String email) {
        Optional<OtpEntity> otpEntityOpt = otpRepository.findByEmail(email);
        if (otpEntityOpt.isEmpty()) {
            return -1L;
        }

        OtpEntity otpEntity = otpEntityOpt.get();
        if (otpEntity.isExpired()) {
            return 0L;
        }

        long secondsRemaining = java.time.temporal.ChronoUnit.SECONDS
                .between(LocalDateTime.now(), otpEntity.getExpiryTime());
        return Math.max(0, secondsRemaining);
    }

    /**
     * Generate random 6-digit OTP
     */
    private String generateOtp() {
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementFailedAttempt(String email) {
        Optional<OtpEntity> otpOpt = otpRepository.findByEmail(email);
        if (otpOpt.isPresent()) {
            OtpEntity otpEntity = otpOpt.get();
            otpEntity.setFailedAttempts(otpEntity.getFailedAttempts() + 1);
            otpRepository.save(otpEntity);
            log.warn("Invalid OTP attempt for email: [{}]. Attempts: [{}]",
                    email, otpEntity.getFailedAttempts());
        }
    }
}