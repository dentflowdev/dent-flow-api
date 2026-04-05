package com.dentalManagement.dentalFlowBackend.repository;


import com.dentalManagement.dentalFlowBackend.model.OtpEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<OtpEntity, UUID> {

    /**
     * Find OTP by email
     */
    Optional<OtpEntity> findByEmail(String email);

    /**
     * Check if OTP exists for email
     */
    boolean existsByEmail(String email);

    /**
     * Delete all expired OTPs
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpEntity o WHERE o.expiryTime < :currentTime")
    void deleteExpiredOtps(@Param("currentTime") LocalDateTime currentTime);
}
