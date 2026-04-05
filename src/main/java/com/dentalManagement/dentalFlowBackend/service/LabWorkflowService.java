package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.exception.ResourceNotFoundException;
import com.dentalManagement.dentalFlowBackend.model.LabMaterial;
import com.dentalManagement.dentalFlowBackend.model.LabMaterialCategory;
import com.dentalManagement.dentalFlowBackend.model.LabWorkflow;
import com.dentalManagement.dentalFlowBackend.repository.LabMaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for lab workflow and material lookups.
 *
 * Responsibilities:
 * 1. Find workflow based on selected materials
 * 2. Resolve material to category
 * 3. Get category's workflow
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LabWorkflowService {

    private final LabMaterialRepository labMaterialRepository;

    /**
     * Determines the workflow based on the first material selected in an order.
     *
     * How it works:
     * 1. User selects materials (e.g. ["Crown", "E-Max"])
     * 2. We take the FIRST material from the list
     * 3. Find which category it belongs to (e.g. "Crown & Bridge")
     * 4. Get that category's ONE workflow
     * 5. Attach that workflow to the order
     *
     * Why first material?
     * - An order can list multiple materials, but they typically belong to the same category
     * - If they span categories (edge case), the first one determines the flow
     * - All stages are designed to handle any material in that category
     *
     * @param materials List of material names (e.g. ["Crown", "E-Max"])
     * @param labId The lab that "owns" these materials
     * @return The LabWorkflow for the determined category
     * @throws ResourceNotFoundException if material/workflow not found
     */
    public LabWorkflow resolveWorkflowFromMaterials(List<String> materials, UUID labId) {

        if (materials == null || materials.isEmpty()) {
            log.warn("No materials provided for order. Using default workflow.");
            return null; // Will use default during stage update
        }

        String firstMaterial = materials.get(0).trim();;
        log.info("Resolving workflow for lab {} using first material: {}", labId, firstMaterial);

        // Find the material and its category
        Optional<LabMaterial> materialOpt = labMaterialRepository
                .findByMaterialNameAndLabId(firstMaterial, labId);

        if (!materialOpt.isPresent()) {
            log.warn("Material '{}' not found for lab {}. Using default workflow.",
                    firstMaterial, labId);
            return null; // Will use default during stage update
        }

        LabMaterial material = materialOpt.get();
        LabMaterialCategory category = material.getCategory();

        if (category.getWorkflow() == null) {
            log.warn("No workflow found for category '{}'. Using default workflow.",
                    category.getCategoryName());
            return null; // Will use default during stage update
        }

        log.info("Resolved workflow: {} (category: {})",
                category.getWorkflow().getWorkflowName(),
                category.getCategoryName());

        return category.getWorkflow();
    }

    /**
     * Alternative: Resolve workflow by explicit category name.
     * Useful if you want direct category selection in the UI.
     *
     * @param categoryName The exact category name (e.g. "Crown & Bridge")
     * @param labId The lab ID
     * @return The LabWorkflow
     * @throws ResourceNotFoundException if not found
     */
    public LabWorkflow resolveWorkflowByCategory(String categoryName, UUID labId) {
        return labMaterialRepository
                .findWorkflowByCategoryAndLab(categoryName, labId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workflow not found for category '" + categoryName + "' in lab " + labId
                ));
    }
}