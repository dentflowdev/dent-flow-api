package com.dentalManagement.dentalFlowBackend.model;


import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The workflow (sequence of stages) for a given material category in a specific lab.
 * One workflow per category per lab.
 *
 * Inserted via SQL post-registration.
 */
@Entity
@Table(name = "lab_workflows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // One workflow per category — enforced by OneToOne on the category side
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false, unique = true)
    private LabMaterialCategory category;

    @Column(name = "workflow_name")
    private String workflowName;  // optional label, e.g. "Standard Crown Flow"

    // Ordered stages for this workflow
    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stageOrder ASC")
    @Builder.Default
    private List<LabWorkflowStage> stages = new ArrayList<>();
}
