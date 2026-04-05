package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.LabWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabWorkflowRepository extends JpaRepository<LabWorkflow, UUID> {

    @Query("SELECT wf FROM LabWorkflow wf " +
            "JOIN wf.category c " +
            "WHERE c.categoryName = :categoryName")
    Optional<LabWorkflow> findByCategoryName(@Param("categoryName") String categoryName);
}
