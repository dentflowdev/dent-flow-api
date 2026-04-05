package com.dentalManagement.dentalFlowBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A single ordered stage within a lab workflow.
 * Stage names are free text (not from the OrderStage enum) so labs can define
 * completely custom stage names without being tied to the global enum.
 *
 * e.g. "POURING", "SCANNING", "SPECIAL_TRAY", "BITE", "TEETH_SETTING" etc.
 *
 * Inserted via SQL post-registration.
 */
@Entity
@Table(name = "lab_workflow_stages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabWorkflowStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private LabWorkflow workflow;

    @Column(name = "stage_name", nullable = false)
    private String stageName;      // free-text, e.g. "POURING", "SPECIAL_TRAY"

    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;    // 1, 2, 3 … defines the sequence

    @Column(name = "stage_label")
    private String stageLabel;     // optional display label, e.g. "Pouring & Trimming"
}
