package com.dentalManagement.dentalFlowBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a category of materials for a specific lab.
 * e.g. "Crown & Bridge", "Removable & Ortho", "Implant Solution"
 *
 * Inserted via SQL post-registration — labs do NOT configure this themselves.
 */
@Entity
@Table(name = "lab_material_categories",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lab_id", "category_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabMaterialCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    @Column(name = "category_name", nullable = false)
    private String categoryName;   // e.g. "Crown & Bridge"

    @Column(name = "display_order")
    private Integer displayOrder;  // for ordering in UI

    // All materials under this category for this lab
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LabMaterial> materials = new ArrayList<>();

    // The workflow tied to this category for this lab
    @OneToOne(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private LabWorkflow workflow;
}
