package com.dentalManagement.dentalFlowBackend.repository;


import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    // Lab-scoped queries — use JPQL JOIN since labs is @ManyToMany
    // ─────────────────────────────────────────────────────────

    @Query("SELECT u FROM User u JOIN u.labs l WHERE l = :lab AND u.isActive = true")
    List<User> findByLabAndIsActiveTrue(@Param("lab") Lab lab);

    @Query("SELECT u FROM User u JOIN u.labs l WHERE u.username = :username AND l = :lab")
    Optional<User> findByUsernameAndLab(@Param("username") String username, @Param("lab") Lab lab);

    @Query("SELECT u FROM User u JOIN u.labs l WHERE LOWER(u.username) = LOWER(:username) AND l = :lab")
    Optional<User> findByUsernameIgnoreCaseAndLab(@Param("username") String username, @Param("lab") Lab lab);
}
