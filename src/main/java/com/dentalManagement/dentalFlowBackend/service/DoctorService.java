package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.request.CreateDoctorRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.DoctorResponse;
import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import com.dentalManagement.dentalFlowBackend.enums.SseEventType;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.repository.DoctorRepository;
import com.dentalManagement.dentalFlowBackend.repository.UserRepository;
import com.dentalManagement.dentalFlowBackend.util.GetAuthenticatedUser;

import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final GetAuthenticatedUser getAuthenticatedUser;
    private final SseEventPublisher ssePublisher;

    // ─────────────────────────────────────────────────────────
    // CREATE DOCTOR
    //
    // Lab admin adds a doctor. If the email matches an existing
    // ROLE_DOCTOR user, the doctor is auto-linked to that user
    // and the user's labs are updated with this lab.
    // ─────────────────────────────────────────────────────────
    @Transactional
    public DoctorResponse createDoctor(CreateDoctorRequest request) {

        User currentAdmin = getAuthenticatedUser.execute();
        Lab currentLab = currentAdmin.getPrimaryLab();

        log.info("Creating doctor — name: {}, location: {}, lab: {}",
                request.getDoctorName(), request.getLocation(), currentLab.getId());

        // Prevent duplicate email within the same lab.
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            doctorRepository.findByEmailAndLab(request.getEmail(), currentLab).ifPresent(existing -> {
                throw new IllegalArgumentException(
                        "Email already registered with a doctor: " + existing.getDoctorName());
            });
        }

        Doctor doctor = Doctor.builder()
                .doctorName(request.getDoctorName())
                .location(request.getLocation())
                .email(request.getEmail())
                .lab(currentLab)
                .build();

        // If email provided, check for an existing ROLE_DOCTOR user and link them.
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            linkDoctorUserIfExists(doctor, request.getEmail(), currentLab);
        }

        Doctor saved = doctorRepository.save(doctor);
        log.info("Doctor created — id: {}", saved.getId());

        // ── SSE: lab staff see new doctor in the dropdown ──
        ssePublisher.publishToLab(currentLab.getId(), SseEventType.DOCTOR_ADDED,
                Map.of("doctorId", saved.getId(), "doctorName", saved.getDoctorName()));

        return DoctorResponse.from(saved);
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE DOCTOR
    //
    // If the email is being set or changed and matches a
    // ROLE_DOCTOR user, auto-link them.
    // ─────────────────────────────────────────────────────────
    @Transactional
    public DoctorResponse updateDoctor(UUID doctorId, CreateDoctorRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with ID: " + doctorId));

        doctor.setDoctorName(request.getDoctorName());
        doctor.setLocation(request.getLocation());

        String newEmail = request.getEmail();
        boolean emailChanged = newEmail != null && !newEmail.equals(doctor.getEmail());
        doctor.setEmail(newEmail);

        // If email changed or newly added, check for a ROLE_DOCTOR user to link.
        if (emailChanged && !newEmail.isBlank()) {
            linkDoctorUserIfExists(doctor, newEmail, doctor.getLab());
        }

        log.info("Doctor updated — id: {}", doctor.getId());
        Doctor updatedDoctor = doctorRepository.save(doctor);

        // ── SSE: lab staff see updated doctor info ──
        ssePublisher.publishToLab(updatedDoctor.getLab().getId(), SseEventType.DOCTOR_UPDATED,
                Map.of("doctorId", updatedDoctor.getId(), "doctorName", updatedDoctor.getDoctorName()));

        return DoctorResponse.from(updatedDoctor);
    }

    // ─────────────────────────────────────────────────────────
    // DELETE DOCTOR
    // ─────────────────────────────────────────────────────────
    @Transactional
    public void deleteDoctor(UUID doctorId) {

        log.info("Deleting doctor: {}", doctorId);

        // Fetch to get labId for SSE BEFORE deletion
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + doctorId));

        UUID labId = doctor.getLab().getId();

        doctorRepository.deleteById(doctorId);
        log.info("Doctor {} successfully deleted", doctorId);

        // ── SSE: lab staff see doctor removed from dropdown ──
        ssePublisher.publishToLab(labId, SseEventType.DOCTOR_DELETED,
                Map.of("doctorId", doctorId));
    }

    // ─────────────────────────────────────────────────────────
    // GET ALL DOCTORS — scoped to current user's lab
    // ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<DoctorResponse> getAllDoctors() {
        User currentUser = getAuthenticatedUser.execute();
        Lab currentLab = currentUser.getPrimaryLab();

        log.info("Fetching doctors for lab: {}", currentLab.getId());
        List<Doctor> doctors = doctorRepository.findByLab(currentLab);
        log.info("Found {} doctor(s)", doctors.size());
        return doctors.stream().map(DoctorResponse::from).toList();
    }

    // ─────────────────────────────────────────────────────────
    // HELPER: Link registered ROLE_DOCTOR user to a Doctor record.
    //
    // Called during createDoctor / updateDoctor when an email is set.
    // Checks if a user with ROLE_DOCTOR and that email exists.
    // If yes:
    //   - Sets doctor.user = that user
    //   - Adds the doctor's lab to that user's labs (if not already linked)
    // ─────────────────────────────────────────────────────────
    void linkDoctorUserIfExists(Doctor doctor, String email, Lab lab) {
        userRepository.findByEmailAndRole(email, RoleName.ROLE_DOCTOR).ifPresent(doctorUser -> {
            doctor.setUser(doctorUser);

            boolean alreadyLinked = doctorUser.getLabs().stream()
                    .anyMatch(l -> l.getId().equals(lab.getId()));
            if (!alreadyLinked) {
                doctorUser.getLabs().add(lab);
                userRepository.save(doctorUser);
                log.info("Lab {} linked to doctor user {} via doctor add/edit", lab.getId(), doctorUser.getId());
            }
        });
    }
}
