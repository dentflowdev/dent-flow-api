package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.LabMaterialCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabMaterialCategoryRepository extends JpaRepository<LabMaterialCategory, UUID> {

    /**
     * Single query: fetches all categories + their materials for a given lab,
     * ordered by category display order.
     *
     * Materials are loaded via a separate fetch join query below to avoid
     * the Hibernate "HHH90003004" CartesianProduct warning when joining
     * multiple collections in one query.
     */
    @Query("""
            SELECT DISTINCT c
            FROM LabMaterialCategory c
            LEFT JOIN FETCH c.materials m
            WHERE c.lab.id = :labId
            ORDER BY c.displayOrder ASC
            """)
    List<LabMaterialCategory> findAllByLabIdWithMaterials(@Param("labId") UUID labId);

    /**
     * Separate query to load workflows + stages for the same lab.
     * Avoids Cartesian product with the materials collection above.
     */
    @Query("""
            SELECT DISTINCT c
            FROM LabMaterialCategory c
            LEFT JOIN FETCH c.workflow w
            LEFT JOIN FETCH w.stages s
            WHERE c.lab.id = :labId
            ORDER BY c.displayOrder ASC
            """)
    List<LabMaterialCategory> findAllByLabIdWithWorkflows(@Param("labId") UUID labId);
}
