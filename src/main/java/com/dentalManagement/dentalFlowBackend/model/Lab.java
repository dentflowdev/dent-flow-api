package com.dentalManagement.dentalFlowBackend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "labs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "pincode", nullable = false, length = 10)
    private String pincode;

    @Column(name = "mobile_number", nullable = false, length = 15)
    private String mobileNumber;

    // This is the login credential for the lab admin account.
    // labUsername == email (same value stored in both for clarity)
    @Column(name = "lab_username", nullable = false, unique = true)
    private String labUsername;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    // Random unique 5-digit code used by employees during self-registration
    // to associate themselves with this lab.
    // Generated at registration time; stored as plain string (no need to hash).
    @Column(name = "lab_code", nullable = false, unique = true, length = 5)
    private String labCode;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}