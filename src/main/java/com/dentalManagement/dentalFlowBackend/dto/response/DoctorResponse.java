package com.dentalManagement.dentalFlowBackend.dto.response;

import com.dentalManagement.dentalFlowBackend.model.Doctor;
import java.util.UUID;

public record DoctorResponse(
        UUID id,
        String doctorName,
        String location,
        String email
) {
    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getDoctorName(),
                doctor.getLocation(),
                doctor.getEmail()
        );
    }
}
