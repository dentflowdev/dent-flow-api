package com.dentalManagement.dentalFlowBackend.scheduler;


import com.dentalManagement.dentalFlowBackend.repository.OtpRepository;
import com.dentalManagement.dentalFlowBackend.repository.ResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final OtpRepository otpRepository;
    private final ResetTokenRepository resetTokenRepository;

    /**
     * Daily cleanup at 2:00 AM
     * Runs every day at 02:00:00 (2 AM)
     * Cron format: second minute hour day month dayOfWeek
     * "0 0 2 * * *" = Every day at 2:00 AM
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")  // 3:00 AM IST every day
    public void dailyCleanup() {
        try {
            LocalDateTime currentTime = LocalDateTime.now();

            // Delete all expired OTPs
            otpRepository.deleteExpiredOtps(currentTime);
            log.info("Cleanup: Deleted expired OTPs at [{}]", currentTime);

            // Delete all expired reset tokens
            resetTokenRepository.deleteExpiredTokens(currentTime);
            log.info("Cleanup: Deleted expired reset tokens at [{}]", currentTime);

            // Delete used tokens older than 24 hours (for storage optimization)
            LocalDateTime cutoffTime = currentTime.minusHours(24);
            resetTokenRepository.deleteOldUsedTokens(cutoffTime);
            log.info("Cleanup: Deleted old used reset tokens (older than 24h) at [{}]", currentTime);

            log.info("Daily cleanup completed successfully at [{}]", currentTime);

        } catch (Exception e) {
            log.error("Error during daily cleanup: [{}]", e.getMessage(), e);
        }
    }
}
