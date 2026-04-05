package com.dentalManagement.dentalFlowBackend.controller;


import com.dentalManagement.dentalFlowBackend.dto.request.ForgotPasswordRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.ForgotPasswordResponse;
import com.dentalManagement.dentalFlowBackend.service.OtpService;
import com.dentalManagement.dentalFlowBackend.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/forgotpassword")
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordController {

    private final OtpService otpService;
    private final PasswordResetService passwordResetService;

    /**
     * Step 1: Send OTP to email
     * POST /api/v1/forgotpassword/send/otp
     */
    @PostMapping("/send/otp")
    public ResponseEntity<ForgotPasswordResponse.SendOtpResponse> sendOtp(
            @Valid @RequestBody ForgotPasswordRequest.SendOtpRequest request) {
        try {
            String email = request.getEmail();
            log.info("OTP send request for email: [{}]", email);

            // Generate and send OTP
            otpService.generateAndSendOtp(email);

            ForgotPasswordResponse.SendOtpResponse response = ForgotPasswordResponse.SendOtpResponse.builder()
                    .message("OTP sent successfully to your email")
                    .email(email)
                    .expiresIn(5 * 60) // 5 minutes in seconds
                    .status("OTP_SENT")
                    .build();

            log.info("OTP sent successfully for email: [{}]", email);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error sending OTP: [{}]", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Step 2: Resend OTP (if user didn't receive it)
     * POST /api/v1/forgotpassword/resend/otp
     */
    @PostMapping("/resend/otp")
    public ResponseEntity<ForgotPasswordResponse.ResendOtpResponse> resendOtp(
            @Valid @RequestBody ForgotPasswordRequest.ResendOtpRequest request) {
        try {
            String email = request.getEmail();
            log.info("OTP resend request for email: [{}]", email);

            // Resend OTP
            otpService.resendOtp(email);

            ForgotPasswordResponse.ResendOtpResponse response = ForgotPasswordResponse.ResendOtpResponse.builder()
                    .message("New OTP sent to your email")
                    .email(email)
                    .expiresIn(5 * 60) // 5 minutes in seconds
                    .status("OTP_RESENT")
                    .build();

            log.info("OTP resent successfully for email: [{}]", email);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error resending OTP: [{}]", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Step 3: Verify OTP
     * POST /api/v1/forgotpassword/verify/otp
     * Returns a reset token if OTP is valid
     */
    @PostMapping("/verify/otp")
    public ResponseEntity<ForgotPasswordResponse.VerifyOtpResponse> verifyOtp(
            @Valid @RequestBody ForgotPasswordRequest.VerifyOtpRequest request) {
        try {
            String email = request.getEmail();
            String otp = request.getOtp();
            log.info("OTP verification request for email: [{}]", email);

            // Verify OTP
            boolean isValid = otpService.verifyOtp(email, otp);

            if (isValid) {
                // Generate reset token for password reset
                String resetToken = passwordResetService.generateResetToken(email);

                ForgotPasswordResponse.VerifyOtpResponse response = ForgotPasswordResponse.VerifyOtpResponse.builder()
                        .message("OTP verified successfully. Use this token to reset your password.")
                        .resetToken(resetToken)
                        .expiresIn(60 * 60) // 1 hour in seconds
                        .status("OTP_VERIFIED")
                        .build();

                log.info("OTP verified successfully for email: [{}]", email);
                return ResponseEntity.ok(response);
            }

            throw new RuntimeException("OTP verification failed");

        } catch (Exception e) {
            log.error("Error verifying OTP: [{}]", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Step 4: Reset Password
     * POST /api/v1/forgotpassword/reset
     * Uses the reset token from step 3 to update password
     */
    @PostMapping("/reset")
    public ResponseEntity<ForgotPasswordResponse.ResetPasswordResponse> resetPassword(
            @Valid @RequestBody ForgotPasswordRequest.ResetPasswordRequest request) {
        try {
            String resetToken = request.getResetToken();
            String newPassword = request.getNewPassword();
            String confirmPassword = request.getConfirmPassword();

            log.info("Password reset request with reset token");

            // Reset password
            passwordResetService.resetPassword(resetToken, newPassword, confirmPassword);


            ForgotPasswordResponse.ResetPasswordResponse response = ForgotPasswordResponse.ResetPasswordResponse.builder()
                    .message("Password reset successfully. Please login with your new password.")
                    .status("PASSWORD_RESET_SUCCESS")
                    .success(true)
                    .build();

            log.info("Password reset successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error resetting password: [{}]", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}