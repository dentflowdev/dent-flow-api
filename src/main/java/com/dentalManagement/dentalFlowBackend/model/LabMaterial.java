package com.dentalManagement.dentalFlowBackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A single material option within a category for a specific lab.
 * e.g. "Crown", "Bridge", "Veneer" under "Crown & Bridge"
 *
 * Inserted via SQL post-registration.
 */
@Entity
@Table(name = "lab_materials",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "material_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private LabMaterialCategory category;

    @Column(name = "material_name", nullable = false)
    private String materialName;   // e.g. "Crown", "E-MAX", "PEEK"

    @Column(name = "display_order")
    private Integer displayOrder;
}
