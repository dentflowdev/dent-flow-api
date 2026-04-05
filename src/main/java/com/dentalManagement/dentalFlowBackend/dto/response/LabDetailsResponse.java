package com.dentalManagement.dentalFlowBackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LabDetailsResponse {

    private UUID labId;
    private String name;
    private String city;
    private String state;
    private String address;
    private String pincode;
    private String mobileNumber;
    private String email;
    private String labCode;

    private List<CategoryDto> categories;

    @Data
    @Builder
    public static class CategoryDto {
        private UUID categoryId;
        private String categoryName;
        private int displayOrder;
        private List<String> materials;
        private List<StageDto> workflowStages;
    }

    @Data
    @Builder
    public static class StageDto {
        private String stageName;
        private String stageLabel;
        private int stageOrder;
    }
}
