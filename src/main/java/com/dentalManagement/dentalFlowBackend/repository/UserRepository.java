package com.dentalManagement.dentalFlowBackend.repository;


import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    // ─────────────────────────────────────────────────────────
    // NEW METHODS: Filter users by lab
    // ─────────────────────────────────────────────────────────

    /**
     * Find all active users belonging to a specific lab
     * @param lab The lab entity
     * @return List of active users in that lab
     */
    List<User> findByLabAndIsActiveTrue(Lab lab);

    /**
     * Find a user by username within a specific lab
     * @param username The username to search for
     * @param lab The lab entity
     * @return Optional containing the user if found
     */
    Optional<User> findByUsernameAndLab(String username, Lab lab);

    /**
     * Find a user by username (case-insensitive) within a specific lab
     * @param username The username to search for (case-insensitive)
     * @param lab The lab entity
     * @return Optional containing the user if found
     */
    Optional<User> findByUsernameIgnoreCaseAndLab(String username, Lab lab);
}
