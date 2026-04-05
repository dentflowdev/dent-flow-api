package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.dto.response.StageCountDtoResponse;
import com.dentalManagement.dentalFlowBackend.enums.OrderStatus;
import com.dentalManagement.dentalFlowBackend.objectMapper.ResponseMapper;
import com.dentalManagement.dentalFlowBackend.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    private final OrderRepository orderRepository;
    private final ResponseMapper responseMapper;

    /**
     * Get count of all stages in IN_PROGRESS status with stage labels
     * @return List of StageCountDto with stage names, labels, and their counts
     */
    public List<StageCountDtoResponse> getStageCountsForInProgress() {
        log.info("Fetching stage counts for IN_PROGRESS orders");

        List<Object[]> results = orderRepository.findStageCountByStatus(OrderStatus.IN_PROGRESS);

        return responseMapper.mapResultsToStageCountDto(results);
    }
}
