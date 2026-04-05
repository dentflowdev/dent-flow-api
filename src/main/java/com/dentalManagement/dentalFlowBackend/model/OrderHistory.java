package com.dentalManagement.dentalFlowBackend.model;

import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    // Snapshot of role at the time of change
    // Even if user's role changes later, history stays accurate
    @Column(name = "role_at_time", nullable = false)
    private String roleAtTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private OrderStatus previousStatus;

    // ── Stage fields (CHANGED TO STRING) ────────────────────────
    // Changed from OrderStage enum to String because:
    // - Stages now come from database (LabWorkflowStage.stageName)
    // - Different workflows have different stages
    // - Database already stores as VARCHAR(255) text
    // - Safe change: no data loss, enum values already stored as text
    @Column(name = "previous_stage")
    private String previousStage;

    @Column(name = "new_stage")
    private String newStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private OrderStatus newStatus;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
