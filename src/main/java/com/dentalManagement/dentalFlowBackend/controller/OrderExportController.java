package com.dentalManagement.dentalFlowBackend.controller;

import com.dentalManagement.dentalFlowBackend.dto.request.OrderExportRequest;
import com.dentalManagement.dentalFlowBackend.service.OrderExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@Slf4j
public class OrderExportController {

    private final OrderExportService orderExportService;

    private static final DateTimeFormatter FILE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/orders/export/excel
    // Downloads orders for a lab within a date range as an
    // Excel file (.xlsx) with one sheet per doctor + "All Orders".
    //
    // Body (JSON):
    //   labId     : UUID            (required)
    //   startDate : LocalDateTime   (required)
    //   endDate   : LocalDateTime   (required)
    // ─────────────────────────────────────────────────────────
    @PostMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(@RequestBody @Valid OrderExportRequest request) {

        log.info("POST /api/v1/orders/export/excel — labId={}, startDate={}, endDate={}",
                request.getLabId(), request.getStartDate(), request.getEndDate());

        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be after startDate");
        }

        try {
            ByteArrayOutputStream out = orderExportService.generateOrdersExcel(
                    request.getLabId(), request.getStartDate(), request.getEndDate());

            String filename = String.format("orders_%s_to_%s.xlsx",
                    request.getStartDate().format(FILE_DATE_FORMATTER),
                    request.getEndDate().format(FILE_DATE_FORMATTER));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("no-cache, no-store, must-revalidate");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(out.toByteArray());

        } catch (IOException e) {
            log.error("Failed to generate Excel export for labId={}", request.getLabId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate Excel file: " + e.getMessage());
        }
    }
}
