package com.dentalManagement.dentalFlowBackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.name}")
    private String appName;

    @Override
    public void sendOtpEmail(String email, String otp, int expiryMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setFrom(fromEmail);
            helper.setSubject("Your OTP for Password Reset - " + appName);

            String htmlContent = getOtpEmailTemplate(otp, expiryMinutes);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("OTP email sent successfully to: [{}]", email);

        } catch (MessagingException e) {
            log.error("Failed to send OTP email to: [{}]. Error: [{}]", email, e.getMessage());
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage());
        }
    }

    @Override
    public void sendPasswordResetSuccessEmail(String email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setFrom(fromEmail);
            helper.setSubject("Password Reset Successful - " + appName);

            String htmlContent = getPasswordResetSuccessEmailTemplate();

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Password reset success email sent to: [{}]", email);

        } catch (MessagingException e) {
            log.error("Failed to send password reset success email to: [{}]", email);
        }
    }

    private String getOtpEmailTemplate(String otp, int expiryMinutes) {
        try {
            String template = loadTemplateFromClasspath("templates/otp-email-template.html");
            return template
                    .replace("${appName}", appName)
                    .replace("${otp}", otp)
                    .replace("${expiryMinutes}", String.valueOf(expiryMinutes));
        } catch (IOException e) {
            log.error("Failed to load OTP email template: [{}]", e.getMessage());
            throw new RuntimeException("Failed to load OTP email template: " + e.getMessage());
        }
    }

    private String getPasswordResetSuccessEmailTemplate() {
        try {
            String template = loadTemplateFromClasspath("templates/password-reset-success-email-template.html");
            return template.replace("${appName}", appName);
        } catch (IOException e) {
            log.error("Failed to load password reset success email template: [{}]", e.getMessage());
            throw new RuntimeException("Failed to load password reset success email template: " + e.getMessage());
        }
    }

    /**
     * Load template from classpath (works both in JAR and file system)
     * ✅ Works in Docker/Cloud Run
     * ✅ Works in development
     * ✅ Works with nested JAR files
     */
    private String loadTemplateFromClasspath(String resourcePath) throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);

            // ✅ Use getInputStream() instead of getFile() - works in JAR files!
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);

        } catch (IOException e) {
            log.error("IOException loading template [{}]: [{}]", resourcePath, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error loading template [{}]: [{}]", resourcePath, e.getMessage());
            throw new IOException("Failed to load template: " + resourcePath, e);
        }
    }
}