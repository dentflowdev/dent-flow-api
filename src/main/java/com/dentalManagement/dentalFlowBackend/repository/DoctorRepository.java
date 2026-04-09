package com.dentalManagement.dentalFlowBackend.repository;

import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // All Doctor records linked to a given User account (one per lab the doctor is associated with).
    List<Doctor> findByUser(User user);

    // Doctor record for a specific user within a specific lab.
    @Query("SELECT d FROM Doctor d WHERE d.user = :user AND d.lab.id = :labId")
    Optional<Doctor> findByUserAndLabId(@Param("user") User user, @Param("labId") UUID labId);
}
