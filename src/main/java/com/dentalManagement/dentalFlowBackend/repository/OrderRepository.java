package com.dentalManagement.dentalFlowBackend.repository;


import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.model.Doctor;
import com.dentalManagement.dentalFlowBackend.model.Lab;
import com.dentalManagement.dentalFlowBackend.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByBarcodeId(String barcodeId);

    // For listing all orders with optional status filter
    Page<Order> findAllByCurrentStatus(OrderStatus status, Pageable pageable);

    // Exact barcode match (unique — returns Optional)
    Optional<Order> findByBarcodeId(String barcodeId);

    // Combined patient name OR doctor name search
    @Query("SELECT o FROM Order o LEFT JOIN o.doctor d WHERE LOWER(o.patientName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(d.doctorName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Order> findAllByPatientNameOrDoctorNameContainingIgnoreCase(@Param("query") String query, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.currentStatus IN :statuses AND o.dueDate < :now")
    Page<Order> findOverdueOrders(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    // ── Lab-filtered queries ──────────────────────────────────

    @Query("SELECT o FROM Order o WHERE :lab MEMBER OF o.createdBy.labs")
    Page<Order> findAllByCreatedByLab(@Param("lab") Lab lab, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.currentStatus = :status AND :lab MEMBER OF o.createdBy.labs")
    Page<Order> findAllByCurrentStatusAndCreatedByLab(@Param("status") OrderStatus status, @Param("lab") Lab lab, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.barcodeId = :barcodeId AND :lab MEMBER OF o.createdBy.labs")
    Optional<Order> findByBarcodeIdAndCreatedByLab(@Param("barcodeId") String barcodeId, @Param("lab") Lab lab);

    @Query("SELECT o FROM Order o LEFT JOIN o.doctor d WHERE (LOWER(o.patientName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(d.doctorName) LIKE LOWER(CONCAT('%', :query, '%'))) AND :lab MEMBER OF o.createdBy.labs")
    Page<Order> findAllByPatientNameOrDoctorNameContainingIgnoreCaseAndLab(@Param("query") String query, @Param("lab") Lab lab, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.currentStatus IN :statuses AND o.dueDate < :now AND :lab MEMBER OF o.createdBy.labs")
    Page<Order> findOverdueOrdersByLab(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("lab") Lab lab,
            Pageable pageable);

    // ── Dentist-scoped queries (orders linked to doctor user across all labs) ──

    @Query("SELECT o FROM Order o WHERE o.doctor IN :doctors")
    Page<Order> findAllByDoctorIn(@Param("doctors") List<Doctor> doctors, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.doctor IN :doctors AND o.currentStatus = :status")
    Page<Order> findAllByDoctorInAndCurrentStatus(@Param("doctors") List<Doctor> doctors, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.barcodeId = :barcodeId AND o.doctor IN :doctors")
    Optional<Order> findByBarcodeIdAndDoctorIn(@Param("barcodeId") String barcodeId, @Param("doctors") List<Doctor> doctors);

    @Query("SELECT o FROM Order o WHERE LOWER(o.patientName) LIKE LOWER(CONCAT('%', :query, '%')) AND o.doctor IN :doctors")
    Page<Order> findAllByPatientNameContainingIgnoreCaseAndDoctorIn(@Param("query") String query, @Param("doctors") List<Doctor> doctors, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.currentStatus IN :statuses AND o.dueDate < :now AND o.doctor IN :doctors")
    Page<Order> findOverdueOrdersByDoctorIn(@Param("statuses") List<OrderStatus> statuses, @Param("now") LocalDateTime now, @Param("doctors") List<Doctor> doctors, Pageable pageable);

    // Dentist orders scoped to a single lab (single Doctor record)
    @Query("SELECT o FROM Order o WHERE o.doctor = :doctor")
    Page<Order> findAllByDoctor(@Param("doctor") Doctor doctor, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.doctor = :doctor AND o.currentStatus = :status")
    Page<Order> findAllByDoctorAndCurrentStatus(@Param("doctor") Doctor doctor, @Param("status") OrderStatus status, Pageable pageable);

    // ── Dentist analytics count queries ──────────────────

    @Query("SELECT COUNT(o) FROM Order o WHERE o.doctor IN :doctors AND o.currentStatus = :status")
    long countByDoctorInAndCurrentStatus(@Param("doctors") List<Doctor> doctors, @Param("status") OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.doctor IN :doctors")
    long countByDoctorIn(@Param("doctors") List<Doctor> doctors);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.currentStatus IN :statuses AND o.dueDate < :now AND o.doctor IN :doctors")
    long countOverdueOrdersByDoctorIn(@Param("statuses") List<OrderStatus> statuses, @Param("now") LocalDateTime now, @Param("doctors") List<Doctor> doctors);

    // ── Lab analytics count queries ───────────────────────

    @Query("SELECT COUNT(o) FROM Order o WHERE :lab MEMBER OF o.createdBy.labs")
    long countByLab(@Param("lab") Lab lab);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.currentStatus = :status AND :lab MEMBER OF o.createdBy.labs")
    long countByStatusAndLab(@Param("status") OrderStatus status, @Param("lab") Lab lab);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.currentStatus IN :statuses AND o.dueDate < :now AND :lab MEMBER OF o.createdBy.labs")
    long countOverdueOrdersByLab(@Param("statuses") List<OrderStatus> statuses, @Param("now") LocalDateTime now, @Param("lab") Lab lab);

    @Query(value = """
            SELECT CAST(o.created_at AS DATE) AS order_date, COUNT(o.id) AS order_count
            FROM orders o
            JOIN user_labs ul ON o.created_by = ul.user_id
            WHERE ul.lab_id = :labId
              AND o.created_at >= :startDate
              AND o.created_at < :endDate
            GROUP BY CAST(o.created_at AS DATE)
            ORDER BY order_date ASC
            """, nativeQuery = true)
    List<Object[]> findDailyOrderCountsByLab(
            @Param("labId") UUID labId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // ── Export queries ────────────────────────────────────

    @Query("SELECT o FROM Order o JOIN o.createdBy.labs l WHERE o.createdAt BETWEEN :startDate AND :endDate AND l.id = :labId ORDER BY o.createdAt ASC")
    List<Order> findByCreatedAtBetweenAndLabId(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("labId") UUID labId);

    // Get stage counts grouped by stage for IN_PROGRESS orders (with stage labels)
    @Query("SELECT o.currentStage as stageName, lws.stageLabel, COUNT(o) as stageCount " +
            "FROM Order o " +
            "LEFT JOIN LabWorkflowStage lws ON o.currentStage = lws.stageName " +
            "  AND o.workflow.id = lws.workflow.id " +
            "WHERE o.currentStatus = :status AND o.currentStage IS NOT NULL " +
            "GROUP BY o.currentStage, lws.stageLabel")
    List<Object[]> findStageCountByStatus(@Param("status") OrderStatus status);
}