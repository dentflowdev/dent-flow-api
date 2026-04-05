package com.dentalManagement.dentalFlowBackend.service;

public interface EmailService {

    /**
     * Send OTP via email
     * @param email recipient email
     * @param otp the 6-digit OTP code
     * @param expiryMinutes OTP validity duration in minutes
     */
    void sendOtpEmail(String email, String otp, int expiryMinutes);

    /**
     * Send password reset confirmation email
     * @param email recipient email
     */
    void sendPasswordResetSuccessEmail(String email);
}