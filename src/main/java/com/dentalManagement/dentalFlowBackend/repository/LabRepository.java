package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.Lab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabRepository extends JpaRepository<Lab, UUID> {

    boolean existsByEmail(String email);

    boolean existsByLabCode(String labCode);

    Optional<Lab> findByLabCode(String labCode);

    Optional<Lab> findByEmail(String email);
}
