package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.DentistOrderRequest;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DentistOrderRequestRepository extends JpaRepository<DentistOrderRequest, UUID> {

    // All pending requests sent to a specific lab
    List<DentistOrderRequest> findByLab(Lab lab);

    // All pending requests submitted by a specific doctor user, newest first
    List<DentistOrderRequest> findByRequestedByOrderByCreatedAtDesc(User requestedBy);
}
