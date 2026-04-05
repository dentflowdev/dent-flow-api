package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.LabMaterial;
import com.dentalManagement.dentalFlowBackend.model.LabWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabMaterialRepository extends JpaRepository<LabMaterial, UUID> {

    /**
     * Find a specific material by name within a lab's category.
     *
     * @param materialName The exact material name (e.g. "Crown", "E-Max")
     * @param labId The lab ID
     * @return Optional containing the LabMaterial if found
     */
    @Query("SELECT m FROM LabMaterial m " +
            "JOIN m.category c " +
            "WHERE UPPER(m.materialName) = UPPER(:materialName) " +
            "AND c.lab.id = :labId")
    Optional<LabMaterial> findByMaterialNameAndLabId(
            @Param("materialName") String materialName,
            @Param("labId") UUID labId
    );

    /**
     * Find a workflow by category name and lab.
     *
     * @param categoryName The category name (e.g. "Crown & Bridge")
     * @param labId The lab ID
     * @return Optional containing the workflow if found
     */
    @Query("SELECT c.workflow FROM LabMaterialCategory c " +
            "WHERE UPPER(c.categoryName) = UPPER(:categoryName) " +
            "AND c.lab.id = :labId")
    Optional<LabWorkflow> findWorkflowByCategoryAndLab(
            @Param("categoryName") String categoryName,
            @Param("labId") UUID labId
    );
}
