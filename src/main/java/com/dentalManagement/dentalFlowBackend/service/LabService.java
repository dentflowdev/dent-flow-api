package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.request.LabRegistrationRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.LabDetailsResponse;
import com.dentalManagement.dentalFlowBackend.dto.response.LabDetailsResponse.CategoryDto;
import com.dentalManagement.dentalFlowBackend.dto.response.LabDetailsResponse.StageDto;
import com.dentalManagement.dentalFlowBackend.dto.response.LabRegistrationResponse;
import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import com.dentalManagement.dentalFlowBackend.model.*;
import com.dentalManagement.dentalFlowBackend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LabService {

    private final LabRepository labRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final LabMaterialCategoryRepository categoryRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public LabRegistrationResponse registerLab(LabRegistrationRequest request) {

        if (labRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A lab with this email already exists.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("This email is already in use by another account.");
        }

        String labCode = generateUniqueLabCode();

        Lab lab = Lab.builder()
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .mobileNumber(request.getMobileNumber())
                .labUsername(request.getEmail())
                .email(request.getEmail())
                .labCode(labCode)
                .isActive(true)
                .build();

        lab = labRepository.save(lab);

        Role adminRole = roleRepository.findByRoleName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not seeded in DB"));
        Role defaultRole = roleRepository.findByRoleName(RoleName.ROLE_DEFAULT_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_DEFAULT_USER not seeded in DB"));

        User adminUser = User.builder()
                .username(request.getEmail())
                .firstName(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .lab(lab)
                .roles(Set.of(adminRole, defaultRole))
                .isActive(true)
                .build();

        adminUser = userRepository.save(adminUser);

        return LabRegistrationResponse.builder()
                .labId(lab.getId())
                .name(lab.getName())
                .email(lab.getEmail())
                .labCode(labCode)
                .adminUsername(adminUser.getUsername())
                .message("Lab registered successfully. Share your lab code with employees for self-registration.")
                .build();
    }

    // ── Lab Details (called right after login) ────────────────────────────────

    /**
     * Resolves the calling user's lab from the JWT principal,
     * then returns all categories, materials, and workflow stages for that lab.
     *
     * Two separate fetch-join queries are used intentionally:
     *   Query 1: categories + materials
     *   Query 2: categories + workflow + stages
     *
     * This avoids a Hibernate/Postgres Cartesian product that occurs when
     * two @OneToMany collections are joined in a single query, which produces
     * duplicate rows and inflated result sets.
     *
     * Results are merged in-memory by category ID — cheap since a lab will
     * typically have fewer than 20 categories.
     */
    @Transactional(readOnly = true)
    public LabDetailsResponse getLabDetailsForCurrentUser() {

        UUID labId = resolveLabIdFromSecurityContext();

        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new NoSuchElementException("Lab not found: " + labId));

        // Query 1 — categories with materials
        List<LabMaterialCategory> withMaterials =
                categoryRepository.findAllByLabIdWithMaterials(labId);

        // Query 2 — categories with workflows and stages
        List<LabMaterialCategory> withWorkflows =
                categoryRepository.findAllByLabIdWithWorkflows(labId);

        // Index workflows by category ID for O(1) merge
        Map<UUID, LabWorkflow> workflowByCategoryId = withWorkflows.stream()
                .filter(c -> c.getWorkflow() != null)
                .collect(Collectors.toMap(
                        LabMaterialCategory::getId,
                        LabMaterialCategory::getWorkflow
                ));

        List<CategoryDto> categoryDtos = withMaterials.stream()
                .map(category -> {

                    List<String> materialNames = category.getMaterials().stream()
                            .sorted(Comparator.comparingInt(
                                    m -> Optional.ofNullable(m.getDisplayOrder()).orElse(0)))
                            .map(LabMaterial::getMaterialName)
                            .collect(Collectors.toList());

                    LabWorkflow workflow = workflowByCategoryId.get(category.getId());
                    List<StageDto> stageDtos = (workflow == null)
                            ? Collections.emptyList()
                            : workflow.getStages().stream()
                            .sorted(Comparator.comparingInt(LabWorkflowStage::getStageOrder))
                            .map(s -> StageDto.builder()
                                    .stageName(s.getStageName())
                                    .stageLabel(s.getStageLabel())
                                    .stageOrder(s.getStageOrder())
                                    .build())
                            .collect(Collectors.toList());

                    return CategoryDto.builder()
                            .categoryId(category.getId())
                            .categoryName(category.getCategoryName())
                            .displayOrder(Optional.ofNullable(category.getDisplayOrder()).orElse(0))
                            .materials(materialNames)
                            .workflowStages(stageDtos)
                            .build();
                })
                .collect(Collectors.toList());

        return LabDetailsResponse.builder()
                .labId(lab.getId())
                .name(lab.getName())
                .city(lab.getCity())
                .state(lab.getState())
                .address(lab.getAddress())
                .pincode(lab.getPincode())
                .mobileNumber(lab.getMobileNumber())
                .email(lab.getEmail())
                .labCode(lab.getLabCode())
                .categories(categoryDtos)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Reads the authenticated user's username from the SecurityContext
     * (populated by JwtAuthFilter), loads the User, and returns their lab UUID.
     */
    private UUID resolveLabIdFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException(
                        "Authenticated user not found: " + username));

        if (user.getLab() == null) {
            throw new IllegalStateException(
                    "User '" + username + "' is not associated with any lab.");
        }
        return user.getLab().getId();
    }

    private String generateUniqueLabCode() {
        String code;
        int attempts = 0;
        do {
            if (++attempts > 20) {
                throw new IllegalStateException(
                        "Could not generate a unique lab code after 20 attempts.");
            }
            code = String.format("%05d", RANDOM.nextInt(100_000));
        } while (labRepository.existsByLabCode(code));
        return code;
    }
}
