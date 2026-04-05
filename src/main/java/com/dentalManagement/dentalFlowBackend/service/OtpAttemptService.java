package com.dentalManagement.dentalFlowBackend.service;


import com.dentalManagement.dentalFlowBackend.model.OtpEntity;
import com.dentalManagement.dentalFlowBackend.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpAttemptService {
    private final OtpRepository otpRepository;

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