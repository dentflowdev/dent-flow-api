package com.dentalManagement.dentalFlowBackend.model;

import com.dentalManagement.dentalFlowBackend.util.StringListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dentist_order_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DentistOrderRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Patient ───────────────────────────────────────────────
    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    // ── Clinical ──────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Convert(converter = StringListConverter.class)
    @Column(name = "teeth", columnDefinition = "TEXT")
    private List<String> teeth;

    @Convert(converter = StringListConverter.class)
    @Column(name = "shade", columnDefinition = "TEXT")
    private List<String> shade;

    @Convert(converter = StringListConverter.class)
    @Column(name = "materials", columnDefinition = "TEXT")
    private List<String> materials;

    // ── Additional ────────────────────────────────────────────
    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "image_url")
    private String imageUrl;

    // ── Target lab ────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    // ── Audit ─────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
