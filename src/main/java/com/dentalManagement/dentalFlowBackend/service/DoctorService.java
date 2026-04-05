package com.dentalManagement.dentalFlowBackend.service;


import com.dentalManagement.dentalFlowBackend.dto.request.CreateDoctorRequest;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.repository.DoctorRepository;
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

    // ─────────────────────────────────────────────────────────
    // CREATE DOCTOR
    // ─────────────────────────────────────────────────────────
    @Transactional
    public Doctor createDoctor(CreateDoctorRequest request) {

        log.info("Creating doctor — name: {}, location: {}", request.getDoctorName(), request.getLocation());

        Doctor doctor = Doctor.builder()
                .doctorName(request.getDoctorName())
                .location(request.getLocation())
                .email(request.getEmail())
                .build();

        Doctor saved = doctorRepository.save(doctor);
        log.info("Doctor created — id: {}", saved.getId());
        return saved;
    }
    public Doctor updateDoctor(UUID doctorId, CreateDoctorRequest request) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with ID: " + doctorId));

        doctor.setDoctorName(request.getDoctorName());
        doctor.setLocation(request.getLocation());
        doctor.setEmail(request.getEmail());
        log.info("Doctor edited — id: {}", doctor.getId());
        return doctorRepository.save(doctor);
    }

    // ─────────────────────────────────────────────────────────
    // DELETE DOCTOR
    // ─────────────────────────────────────────────────────────
    @Transactional
    public void deleteDoctor(UUID doctorId) {

        log.info("Deleting doctor: {}", doctorId);

        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found: " + doctorId);
        }

        doctorRepository.deleteById(doctorId);
        log.info("Doctor {} successfully deleted", doctorId);
    }

    @Transactional(readOnly = true)
    public List<Doctor> getAllDoctors() {

        log.info("Fetching all doctors");
        List<Doctor> doctors = doctorRepository.findAll();
        log.info("Found {} doctor(s)", doctors.size());
        return doctors;
    }
}
