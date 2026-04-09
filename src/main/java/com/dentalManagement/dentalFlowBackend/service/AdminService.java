package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.request.AssignRoleRequest;
import com.dentalManagement.dentalFlowBackend.dto.response.UserResponse;
import com.dentalManagement.dentalFlowBackend.enums.RoleName;
import com.dentalManagement.dentalFlowBackend.exception.OperationNotPermittedException;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.Role;
import com.dentalManagement.dentalFlowBackend.model.User;
import com.dentalManagement.dentalFlowBackend.objectMapper.ResponseMapper;
import com.dentalManagement.dentalFlowBackend.repository.LabRepository;
import com.dentalManagement.dentalFlowBackend.repository.RefreshTokenRepository;
import com.dentalManagement.dentalFlowBackend.repository.RoleRepository;
import com.dentalManagement.dentalFlowBackend.repository.UserRepository;
import com.dentalManagement.dentalFlowBackend.util.GetAuthenticatedUser;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final ResponseMapper responseMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleRepository roleRepository;
    private final LabRepository labRepository;
    private final GetAuthenticatedUser getAuthenticatedUser;  // ← NEW: Inject to get current admin

    /**
     * Get all active users belonging to the current admin's lab, excluding ROLE_DOCTOR users.
     * Doctor-role users are linked to labs externally and are not managed through admin operations.
     */
    public List<UserResponse> getAllUsers(){
        User currentAdmin = getAuthenticatedUser.execute();
        Lab currentAdminLab = currentAdmin.getPrimaryLab();

        log.info("Fetching all users for lab: {}", currentAdminLab.getId());

        return userRepository.findByLabAndIsActiveTrueExcludingRole(currentAdminLab, RoleName.ROLE_DOCTOR)
                .stream()
                .map(responseMapper::toUserResponse)
                .collect(Collectors.toList());
    }

    private void assertNotDoctor(User user) {
        boolean isDoctor = user.getRoles().stream()
                .anyMatch(role -> role.getRoleName() == RoleName.ROLE_DOCTOR);
        if (isDoctor) {
            throw new OperationNotPermittedException(
                    "Operations on doctor-role users are not permitted through this endpoint."
            );
        }
    }

    /**
     * Deactivate a user (prevents deactivation of lab admins)
     * @param userId The ID of the user to deactivate
     */
    public void deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Ensure the user belongs to the current admin's lab
        User currentAdmin = getAuthenticatedUser.execute();
        if (!user.getPrimaryLab().getId().equals(currentAdmin.getPrimaryLab().getId())) {
            log.warn("Attempt to deactivate user {} from different lab by admin {}", userId, currentAdmin.getId());
            throw new OperationNotPermittedException(
                    "You can only manage users within your own lab."
            );
        }

        assertNotDoctor(user);

        String userEmail = user.getEmail();
        boolean emailExists = labRepository.existsByEmail(userEmail);
        if(emailExists){
            log.warn("Attempt to deactivate lab admin user {} with email {}", userId, userEmail);
            throw new OperationNotPermittedException(
                    "This user is a Lab Admin and cannot be deactivated. Please contact the Tech Team."
            );
        }
        user.setActive(false);
        userRepository.save(user);
        log.info("User {} has been deactivated", userId);
    }

    /**
     * Get a user by username (only from current admin's lab)
     * @param username The username to search for
     * @return UserResponse object for the found user
     */
    public UserResponse getUserByUserName(String username){
        // Get the current authenticated admin
        User currentAdmin = getAuthenticatedUser.execute();
        Lab currentAdminLab = currentAdmin.getPrimaryLab();

        log.info("Fetching user {} from lab: {}", username, currentAdminLab.getId());

        // Find user by username within the same lab
        User user = userRepository.findByUsernameIgnoreCaseAndLab(username, currentAdminLab)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with username: " + username + " in your lab"
                ));

        assertNotDoctor(user);
        return responseMapper.toUserResponse(user);
    }

    /**
     * Delete a user by ID (only from current admin's lab)
     * @param userId The ID of the user to delete
     */
    public void deleteUserById(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Ensure the user belongs to the current admin's lab
        User currentAdmin = getAuthenticatedUser.execute();
        if (!user.getPrimaryLab().getId().equals(currentAdmin.getPrimaryLab().getId())) {
            log.warn("Attempt to delete user {} from different lab by admin {}", userId, currentAdmin.getId());
            throw new OperationNotPermittedException(
                    "You can only manage users within your own lab."
            );
        }

        assertNotDoctor(user);

        refreshTokenRepository.deleteByUser(user);
        userRepository.deleteById(userId);
        log.info("User {} has been deleted", userId);
    }

    // ─────────────────────────────────────────────────────────
    // ASSIGN / REPLACE ROLE (Admin only)
    //
    // Each user always has ROLE_DEFAULT_USER.
    // If user has only ROLE_DEFAULT_USER → add the new role.
    // If user already has a second role   → replace it with the new one.
    // ROLE_DEFAULT_USER cannot be assigned via this endpoint.
    // Only admins can assign roles to users within their own lab.
    // ─────────────────────────────────────────────────────────
    @Transactional
    public void assignRole(UUID userId, AssignRoleRequest request) {

        log.info("Assigning role {} to user: {}", request.getRoleName(), userId);

        if (request.getRoleName() == RoleName.ROLE_DEFAULT_USER) {
            throw new IllegalArgumentException("ROLE_DEFAULT_USER cannot be assigned via this endpoint.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Ensure the user belongs to the current admin's lab
        User currentAdmin = getAuthenticatedUser.execute();
        if (!user.getPrimaryLab().getId().equals(currentAdmin.getPrimaryLab().getId())) {
            log.warn("Attempt to assign role to user {} from different lab by admin {}", userId, currentAdmin.getId());
            throw new OperationNotPermittedException(
                    "You can only manage users within your own lab."
            );
        }

        assertNotDoctor(user);

        Role newRole = roleRepository.findByRoleName(request.getRoleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

        // Remove any existing non-default role
        boolean hadSecondRole = user.getRoles().removeIf(
                role -> role.getRoleName() != RoleName.ROLE_DEFAULT_USER
        );

        if (hadSecondRole) {
            log.info("Replaced existing second role for user: {}", userId);
        } else {
            log.info("User {} had only ROLE_DEFAULT_USER — adding new role", userId);
        }

        user.getRoles().add(newRole);
        userRepository.save(user);

        log.info("Role {} successfully assigned to user: {}", request.getRoleName(), userId);
    }
}