package com.dentalManagement.dentalFlowBackend.repository;


import com.dentalManagement.dentalFlowBackend.model.ResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResetTokenRepository extends JpaRepository<ResetTokenEntity, UUID> {

    /**
     * Find reset token by token string
     */
    Optional<ResetTokenEntity> findByResetToken(String resetToken);

    /**
     * Find reset token by token string and check if valid (not expired and not used)
     */
    Optional<ResetTokenEntity> findByResetTokenAndUsedFalse(String resetToken);

    /**
     * Delete all expired reset tokens
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ResetTokenEntity r WHERE r.expiryTime < :currentTime")
    void deleteExpiredTokens(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Delete all used tokens older than specified time (cleanup)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ResetTokenEntity r WHERE r.used = true AND r.updatedAt < :cutoffTime")
    void deleteOldUsedTokens(@Param("cutoffTime") LocalDateTime cutoffTime);
}