package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ForgotPasswordResponse {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendOtpResponse {
        private String message;
        private String email;
        private int expiresIn; // seconds
        private String status; // "OTP_SENT"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifyOtpResponse {
        private String message;
        private String resetToken; // Use this token to reset password
        private int expiresIn; // Token validity in seconds
        private String status; // "OTP_VERIFIED"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordResponse {
        private String message;
        private String status; // "PASSWORD_RESET_SUCCESS"
        private boolean success;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResendOtpResponse {
        private String message;
        private String email;
        private int expiresIn;
        private String status; // "OTP_RESENT"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
        private int status;
    }
}
