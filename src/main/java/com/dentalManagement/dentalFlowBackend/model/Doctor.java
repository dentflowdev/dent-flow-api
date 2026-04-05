package com.dentalManagement.dentalFlowBackend.model;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "doctors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "doctor_name", nullable = false)
    private String doctorName;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(unique = true)
    private String email;
}
