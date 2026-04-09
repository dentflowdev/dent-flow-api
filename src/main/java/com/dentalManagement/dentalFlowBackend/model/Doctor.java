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

    @Column(name = "email")
    private String email;

    // Which lab added this doctor record.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    // Linked User account (null until the doctor self-registers on the app).
    // Once linked, this same User is referenced by all Doctor records sharing the same email.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
