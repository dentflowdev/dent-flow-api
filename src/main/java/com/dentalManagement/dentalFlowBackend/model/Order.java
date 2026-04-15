package com.dentalManagement.dentalFlowBackend.model;

import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.util.StringListConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Convert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Case Details ──────────────────────────────────────────
    @Column(name = "barcode_id", unique = true, nullable = false)
    private String barcodeId;

    @Column(name = "case_number")
    private String caseNumber;

    @Column(name = "box_number")
    private String boxNumber;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "delivery_schedule")
    private String deliverySchedule;

    @Column(name = "order_type")
    private String orderType;

    // ── Patient Details ───────────────────────────────────────
    @Column(name = "patient_name")
    private String patientName;

    // ── Clinical Details ──────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
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

    // ── Additional Details ────────────────────────────────────
    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    // ── Image ─────────────────────────────────────────────────
    @Column(name = "image_url")
    private String imageUrl;

    // ── Status & Stage ────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private OrderStatus currentStatus;

    @Column(name = "current_stage")
    private String currentStage;

    // ── Audit ─────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // ── Edit Flag ─────────────────────────────────────────────
    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private boolean isEdited = false;


    // ── Doctor-placed flag ────────────────────────────────────
    // Null  → not set / unknown
    // true  → order was requested directly by a doctor
    // false → order was placed by staff (e.g., marketing executive)
    @Column(name = "order_placed_by_doctor")
    private Boolean orderPlacedByDoctor;

    // ── Workflow (NEW) ────────────────────────────────────────
    // Links this order to a specific lab workflow.
    // Nullable for backward compatibility with existing orders.
    // If null, a default workflow will be used during stage updates.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private LabWorkflow workflow;
}