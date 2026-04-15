package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.request.CreateDentistOrderRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.DentistOrderRequestResponse;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.DentistOrderRequest;
import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.repository.DentistOrderRequestRepository;
import com.dentalManagement.dentalFlowBackend.repository.DoctorRepository;
import com.dentalManagement.dentalFlowBackend.repository.LabRepository;
import com.dentalManagement.dentalFlowBackend.util.GetAuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DentistOrderRequestService {

    private final DentistOrderRequestRepository dentistOrderRequestRepository;
    private final DoctorRepository doctorRepository;
    private final LabRepository labRepository;
    private final CloudStorageService cloudStorageService;
    private final GetAuthenticatedUser getAuthenticatedUser;

    // ─────────────────────────────────────────────────────────
    // CREATE — Doctor submits a partial order request to a lab
    // ─────────────────────────────────────────────────────────

    @Transactional
    public DentistOrderRequestResponse createRequest(CreateDentistOrderRequest request, MultipartFile image) {

        log.info("Doctor creating order request for lab: {}", request.getLabId());

        // 1. Get authenticated doctor user
        User doctorUser = getAuthenticatedUser.execute();

        // 2. Verify the requested lab is linked to this doctor user
        boolean linkedToLab = doctorUser.getLabs().stream()
                .anyMatch(lab -> lab.getId().equals(request.getLabId()));

        if (!linkedToLab) {
            throw new ResourceNotFoundException(
                    "Lab not found or not linked to your account: " + request.getLabId());
        }

        // 3. Fetch the Lab entity
        Lab lab = labRepository.findById(request.getLabId())
                .orElseThrow(() -> new ResourceNotFoundException("Lab not found: " + request.getLabId()));

        // 4. Find the Doctor record for this user within the requested lab
        Doctor doctor = doctorRepository.findByUserAndLabId(doctorUser, request.getLabId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor profile not found for this lab: " + request.getLabId()));

        ZoneId istZone = ZoneId.of("Asia/Kolkata");

        // 5. Persist the request entity — imageUrl intentionally null until upload succeeds
        DentistOrderRequest orderRequest = DentistOrderRequest.builder()
                .patientName(request.getPatientName())
                .dueDate(request.getDueDate())
                .doctor(doctor)
                .teeth(request.getTeeth())
                .shade(request.getShade())
                .materials(request.getMaterials())
                .instructions(request.getInstructions())
                .imageUrl(null)
                .lab(lab)
                .requestedBy(doctorUser)
                .createdAt(LocalDateTime.now(istZone))
                .build();

        DentistOrderRequest saved = dentistOrderRequestRepository.save(orderRequest);

        // 6. Upload image only after DB save — prevents orphaned GCS objects on DB failure
        if (image != null && !image.isEmpty()) {
            log.info("Uploading image for dentist order request: {}", saved.getId());
            try {
                String imageUrl = cloudStorageService.uploadImage(image);
                saved.setImageUrl(imageUrl);
                saved = dentistOrderRequestRepository.save(saved);
            } catch (Exception e) {
                log.error("Image upload failed for dentist order request {}. Rolling back. Error: {}",
                        saved.getId(), e.getMessage());
                dentistOrderRequestRepository.deleteById(saved.getId());
                throw new RuntimeException("Image upload failed: " + e.getMessage());
            }
        }

        log.info("Dentist order request created. ID: {}, Lab: {}", saved.getId(), lab.getName());

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────
    // GET ALL — Returns all pending requests submitted by this doctor
    // ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DentistOrderRequestResponse> getMyRequests() {

        User doctorUser = getAuthenticatedUser.execute();

        log.info("Fetching all order requests for doctor user: {}", doctorUser.getUsername());

        return dentistOrderRequestRepository
                .findByRequestedByOrderByCreatedAtDesc(doctorUser)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────
    // DELETE — Called when the lab accepts (or cancels) the request
    // ─────────────────────────────────────────────────────────

    @Transactional
    public void deleteRequest(UUID requestId) {

        log.info("Deleting dentist order request: {}", requestId);

        User doctorUser = getAuthenticatedUser.execute();

        DentistOrderRequest orderRequest = dentistOrderRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dentist order request not found: " + requestId));

        // Only the doctor who created the request can delete it
        if (!orderRequest.getRequestedBy().getId().equals(doctorUser.getId())) {
            throw new ResourceNotFoundException("Dentist order request not found: " + requestId);
        }

        dentistOrderRequestRepository.deleteById(requestId);
        log.info("Dentist order request {} deleted.", requestId);
    }

    // ─────────────────────────────────────────────────────────
    // HELPER — Map entity → response DTO
    // ─────────────────────────────────────────────────────────

    private DentistOrderRequestResponse toResponse(DentistOrderRequest req) {
        return DentistOrderRequestResponse.builder()
                .id(req.getId())
                .patientName(req.getPatientName())
                .dueDate(req.getDueDate())
                .doctor(DentistOrderRequestResponse.DoctorInfo.builder()
                        .doctorId(req.getDoctor().getId())
                        .doctorName(req.getDoctor().getDoctorName())
                        .build())
                .teeth(req.getTeeth())
                .shade(req.getShade())
                .materials(req.getMaterials())
                .instructions(req.getInstructions())
                .imageUrl(req.getImageUrl())
                .lab(DentistOrderRequestResponse.LabInfo.builder()
                        .labId(req.getLab().getId())
                        .labName(req.getLab().getName())
                        .build())
                .createdAt(req.getCreatedAt())
                .build();
    }
}
