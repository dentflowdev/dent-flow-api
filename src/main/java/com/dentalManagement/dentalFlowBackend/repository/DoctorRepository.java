package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    // All Doctor records with a given email (across all labs).
    List<Doctor> findByEmail(String email);

    // All doctors added by a specific lab.
    List<Doctor> findByLab(Lab lab);

    // Check if a doctor with this email already exists within a specific lab.
    Optional<Doctor> findByEmailAndLab(String email, Lab lab);
}
