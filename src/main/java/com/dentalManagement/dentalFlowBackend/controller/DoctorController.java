package com.dentalManagement.dentalFlowBackend.controller;



import com.dentalManagement.dentalFlowBackend.dto.request.CreateDoctorRequest;
import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
@Slf4j
public class DoctorController {

    private final DoctorService doctorService;

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/doctors
    // Admin creates a new doctor.
    // ─────────────────────────────────────────────────────────
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<Doctor> createDoctor(@RequestBody @Valid CreateDoctorRequest request) {

        log.info("POST /api/v1/doctors — name: {}", request.getDoctorName());
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.createDoctor(request));
    }

    // ─────────────────────────────────────────────────────────
    // PUT /api/v1/doctors/edit/{doctorId}
    // Admin/Receptionist edits an existing doctor by ID.
    // ─────────────────────────────────────────────────────────
    @PutMapping("/edit/{doctorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<Doctor> editDoctor(
            @PathVariable UUID doctorId,
            @RequestBody @Valid CreateDoctorRequest request) {

        log.info("PUT /api/v1/doctors/edit/{} — name: {}", doctorId, request.getDoctorName());
        return ResponseEntity.ok(doctorService.updateDoctor(doctorId, request));
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/v1/doctors/{doctorId}
    // Admin deletes a doctor by ID.
    // ─────────────────────────────────────────────────────────
    @DeleteMapping("/delete/{doctorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<Void> deleteDoctor(@PathVariable UUID doctorId) {

        log.info("DELETE /api/v1/doctors/{}", doctorId);
        doctorService.deleteDoctor(doctorId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/doctors
    // Fetch all doctors — no pagination (typically a small list)
    // ─────────────────────────────────────────────────────────
    @GetMapping("/getalldoctors")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING_EXECUTIVE', 'RECEPTIONIST', 'TECHNICIAN', 'DOCTOR')")
    public ResponseEntity<List<Doctor>> getAllDoctors() {

        log.info("GET /api/v1/doctors");
        return ResponseEntity.ok(doctorService.getAllDoctors());
    }
}
