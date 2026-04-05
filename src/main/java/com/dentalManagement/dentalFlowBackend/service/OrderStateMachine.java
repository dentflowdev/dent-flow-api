package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.exception.InvalidTransitionException;
import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.LabWorkflow;
import com.dentalManagement.dentalFlowBackend.model.LabWorkflowStage;
import com.dentalManagement.dentalFlowBackend.repository.LabWorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * DYNAMIC ORDER STATE MACHINE
 *
 * Enforces legal stage progression for orders across different workflow types.
 * Unlike the old hardcoded flow, this machine:
 *
 * 1. LOADS stages from a LabWorkflow (database-driven)
 * 2. VALIDATES transitions sequentially (no skipping, no backwards)
 * 3. HANDLES FALLBACK to default workflow (Crown & Bridge) for old orders
 *
 * Workflow Attachment:
 *   - NEW orders: workflow is attached based on selected material category
 *   - OLD orders: workflow is NULL; default is used during stage updates
 *
 * Stage Progression:
 *   - ORDER_CREATED (stage=null) → first stage of workflow
 *   - IN_PROGRESS + stage[N]    → stage[N+1]
 *   - IN_PROGRESS + stage[last] → READY (auto, final stage triggers status change)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStateMachine {

    private final LabWorkflowRepository labWorkflowRepository;

    /**
     * Validates whether a requested stage transition is legal for this workflow.
     *
     * @param currentStatus  The order's current OrderStatus
     * @param currentStageName The order's current stage name (from enum or null)
     * @param requestedStageName The requested stage name (from enum or null)
     * @param workflow The LabWorkflow attached to this order (null for old orders → uses default)
     *
     * @throws InvalidTransitionException if the transition is illegal
     */
    public void validateStageTransition(
            OrderStatus currentStatus,
            String currentStageName,
            String requestedStageName,
            LabWorkflow workflow) {

        // Get the workflow to use (provided or default)
        LabWorkflow effectiveWorkflow = workflow != null
                ? workflow
                : getDefaultWorkflow();

        log.info("Validating stage transition | Current: {} | Requested: {} | Workflow: {}",
                currentStageName, requestedStageName,
                effectiveWorkflow.getWorkflowName());

        List<LabWorkflowStage> stages = effectiveWorkflow.getStages();

        if (stages.isEmpty()) {
            throw new InvalidTransitionException(
                    "Workflow '" + effectiveWorkflow.getWorkflowName() + "' has no stages defined.");
        }

        // ──────────────────────────────────────────────────────────
        // CASE 1: First ever stage move (ORDER_CREATED + stage=null)
        // ──────────────────────────────────────────────────────────
        if (currentStatus == OrderStatus.ORDER_CREATED && currentStageName == null) {
            LabWorkflowStage firstStage = stages.get(0);
            if (!firstStage.getStageName().equals(requestedStageName)) {
                throw new InvalidTransitionException(
                        "First stage must be '" + firstStage.getStageName() +
                                "'. Requested: '" + requestedStageName + "'"
                );
            }
            log.info("Valid: Transitioning to first stage '{}'", requestedStageName);
            return; // Valid — status will move to IN_PROGRESS in service
        }

        // ──────────────────────────────────────────────────────────
        // CASE 2: Non-IN_PROGRESS status = cannot update stage
        // ──────────────────────────────────────────────────────────
        if (currentStatus != OrderStatus.IN_PROGRESS) {
            throw new InvalidTransitionException(
                    "Cannot update stage. Order status is '" + currentStatus +
                            "'. Only IN_PROGRESS orders can advance stages."
            );
        }

        // ──────────────────────────────────────────────────────────
        // CASE 3: Sequential stage progression (IN_PROGRESS + current → next)
        // ──────────────────────────────────────────────────────────
        // Find current stage in the workflow
        Optional<LabWorkflowStage> currentStageOpt = stages.stream()
                .filter(s -> s.getStageName().equals(currentStageName))
                .findFirst();

        if (!currentStageOpt.isPresent()) {
            throw new InvalidTransitionException(
                    "Current stage '" + currentStageName +
                            "' not found in workflow '" + effectiveWorkflow.getWorkflowName() + "'"
            );
        }

        LabWorkflowStage currentStage = currentStageOpt.get();
        int currentIndex = currentStage.getStageOrder() - 1; // stageOrder is 1-based

        // If at the last stage, no further moves allowed
        if (currentIndex >= stages.size() - 1) {
            throw new InvalidTransitionException(
                    "Order is already at final stage '" + currentStageName +
                            "' with status READY. No further stage updates allowed."
            );
        }

        // Get the next stage (sequential, no skipping)
        LabWorkflowStage nextStage = stages.get(currentIndex + 1);

        if (!nextStage.getStageName().equals(requestedStageName)) {
            throw new InvalidTransitionException(
                    "Invalid stage transition. Current: '" + currentStageName +
                            "', Requested: '" + requestedStageName +
                            "', Expected next: '" + nextStage.getStageName() + "'"
            );
        }

        log.info("Valid: Advancing from stage '{}' to '{}'",
                currentStageName, requestedStageName);
    }

    /**
     * Determines if a stage is the final stage in its workflow.
     * When the technician sets the final stage, status auto-transitions to READY.
     *
     * @param stageName The stage name to check
     * @param workflow The LabWorkflow to check against (null → uses default)
     * @return true if this is the final stage
     */
    public boolean isFinalStage(String stageName, LabWorkflow workflow) {
        LabWorkflow effectiveWorkflow = workflow != null
                ? workflow
                : getDefaultWorkflow();

        List<LabWorkflowStage> stages = effectiveWorkflow.getStages();
        if (stages.isEmpty()) {
            return false;
        }

        // Last stage in the ordered list
        LabWorkflowStage lastStage = stages.get(stages.size() - 1);
        return lastStage.getStageName().equals(stageName);
    }

    /**
     * Gets the default workflow (Crown & Bridge).
     * Used for old orders that have no workflow attached (backward compatibility).
     *
     * The seed script creates this as the first workflow.
     * Query: WHERE category.categoryName = 'Crown & Bridge' AND workflow = first of that category
     *
     * @return The default LabWorkflow
     * @throws ResourceNotFoundException if not found
     */
    private LabWorkflow getDefaultWorkflow() {
        return labWorkflowRepository
                .findByCategoryName("Crown & Bridge")
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Default workflow 'Crown & Bridge' not found. " +
                                "Ensure seed_lab_data.sql has been executed."
                ));
    }

    /**
     * Gets all valid stages for a workflow in order.
     * Useful for frontend to display available stages during technician UI.
     *
     * @param workflow The LabWorkflow (null → uses default)
     * @return Ordered list of LabWorkflowStage objects
     */
    public List<LabWorkflowStage> getWorkflowStages(LabWorkflow workflow) {
        LabWorkflow effectiveWorkflow = workflow != null
                ? workflow
                : getDefaultWorkflow();
        return effectiveWorkflow.getStages();
    }
}