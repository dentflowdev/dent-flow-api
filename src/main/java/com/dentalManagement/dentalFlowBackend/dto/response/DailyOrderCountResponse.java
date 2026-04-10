package com.dentalManagement.dentalFlowBackend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class DailyOrderCountResponse {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private long count;
}
