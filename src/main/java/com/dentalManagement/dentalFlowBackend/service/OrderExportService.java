package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.model.Order;
import com.dentalManagement.dentalFlowBackend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExportService {

    private final OrderRepository orderRepository;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] HEADERS = {
            "Barcode ID", "Case Number", "Box Number", "Patient Name", "Doctor Name",
            "Order Type", "Teeth", "Shade", "Materials", "Instructions",
            "Due Date", "Delivery Schedule", "Current Stage", "Created At",
            "Delivered At", "Is Edited"
    };

    // Header style: dark blue background, bold white text
    private static final byte[] HEADER_BG   = {31, 78, 121};
    private static final byte[] HEADER_FG   = {(byte)255, (byte)255, (byte)255};
    // Alternating row colors
    private static final byte[] ROW_ODD     = {(byte)255, (byte)255, (byte)255};
    private static final byte[] ROW_EVEN    = {(byte)242, (byte)242, (byte)242};
    private static final byte[] BORDER_GRAY = {(byte)198, (byte)198, (byte)198};

    @Transactional(readOnly = true)
    public ByteArrayOutputStream generateOrdersExcel(UUID labId, LocalDateTime startDate, LocalDateTime endDate)
            throws IOException {

        List<Order> orders = orderRepository.findByCreatedAtBetweenAndLabId(startDate, endDate, labId);
        log.info("Exporting {} orders for labId={} between {} and {}", orders.size(), labId, startDate, endDate);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // Sheet 1: All Orders
            writeSheet(workbook, "All Orders", orders);

            // Additional sheets: one per unique doctor
            Map<String, List<Order>> byDoctor = orders.stream()
                    .collect(Collectors.groupingBy(
                            o -> o.getDoctorName() != null ? o.getDoctorName() : "Unknown",
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            for (Map.Entry<String, List<Order>> entry : byDoctor.entrySet()) {
                String sheetName = sanitizeSheetName(entry.getKey());
                writeSheet(workbook, sheetName, entry.getValue());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out;
        }
    }

    private void writeSheet(XSSFWorkbook workbook, String sheetName, List<Order> orders) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        sheet.createFreezePane(0, 1); // freeze header row

        // Print margins (in inches)
        sheet.getPrintSetup().setLandscape(false);
        sheet.setMargin(Sheet.LeftMargin, 0.5);
        sheet.setMargin(Sheet.RightMargin, 0.5);
        sheet.setMargin(Sheet.TopMargin, 0.75);
        sheet.setMargin(Sheet.BottomMargin, 0.75);

        CellStyle headerStyle = buildHeaderStyle(workbook);
        CellStyle oddStyle    = buildDataStyle(workbook, ROW_ODD,  false);
        CellStyle evenStyle   = buildDataStyle(workbook, ROW_EVEN, false);
        CellStyle oddCenter   = buildDataStyle(workbook, ROW_ODD,  true);
        CellStyle evenCenter  = buildDataStyle(workbook, ROW_EVEN, true);
        CellStyle oddWrap     = buildWrapStyle(workbook, ROW_ODD);
        CellStyle evenWrap    = buildWrapStyle(workbook, ROW_EVEN);

        // Header row
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(20f);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            boolean isEven = (i % 2 == 1);
            Row row = sheet.createRow(i + 1);
            row.setHeightInPoints(18f);

            CellStyle base   = isEven ? evenStyle  : oddStyle;
            CellStyle center = isEven ? evenCenter : oddCenter;
            CellStyle wrap   = isEven ? evenWrap   : oddWrap;

            setCell(row, 0,  order.getBarcodeId(),       base);
            setCell(row, 1,  order.getCaseNumber(),      base);
            setCell(row, 2,  order.getBoxNumber(),       base);
            setCell(row, 3,  order.getPatientName(),     base);
            setCell(row, 4,  order.getDoctorName(),      base);
            setCell(row, 5,  order.getOrderType(),       base);
            setCell(row, 6,  joinList(order.getTeeth()), wrap);
            setCell(row, 7,  joinList(order.getShade()), wrap);
            setCell(row, 8,  joinList(order.getMaterials()), wrap);
            setCell(row, 9,  order.getInstructions(),    wrap);
            setCell(row, 10, formatDate(order.getDueDate()),       base);
            setCell(row, 11, order.getDeliverySchedule(),          base);
            setCell(row, 12, order.getCurrentStage(),              base);
            setCell(row, 13, formatDate(order.getCreatedAt()),     base);
            setCell(row, 14, formatDate(order.getDeliveredAt()),   base);
            setCellBoolean(row, 15, order.isEdited(),              center);
        }

        // Auto-size columns (min 12, max 50 characters wide)
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            int minWidth = 12 * 256;
            int maxWidth = 50 * 256;
            sheet.setColumnWidth(i, Math.max(minWidth, Math.min(width, maxWidth)));
        }
    }

    // ── Style builders ────────────────────────────────────────────

    private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFColor bg = new XSSFColor(new java.awt.Color(HEADER_BG[0] & 0xFF, HEADER_BG[1] & 0xFF, HEADER_BG[2] & 0xFF), null);
        style.setFillForegroundColor(bg);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(new XSSFColor(new java.awt.Color(HEADER_FG[0] & 0xFF, HEADER_FG[1] & 0xFF, HEADER_FG[2] & 0xFF), null));
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorders(style, IndexedColors.BLACK.getIndex());
        style.setWrapText(false);
        return style;
    }

    private CellStyle buildDataStyle(XSSFWorkbook wb, byte[] rowColor, boolean center) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFColor bg = new XSSFColor(new java.awt.Color(rowColor[0] & 0xFF, rowColor[1] & 0xFF, rowColor[2] & 0xFF), null);
        style.setFillForegroundColor(bg);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);

        style.setAlignment(center ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorderColor(style, wb, BORDER_GRAY);
        style.setWrapText(false);
        return style;
    }

    private CellStyle buildWrapStyle(XSSFWorkbook wb, byte[] rowColor) {
        CellStyle style = buildDataStyle(wb, rowColor, false);
        style.setWrapText(true);
        return style;
    }

    private void applyThinBorders(XSSFCellStyle style, short colorIndex) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(colorIndex);
        style.setBottomBorderColor(colorIndex);
        style.setLeftBorderColor(colorIndex);
        style.setRightBorderColor(colorIndex);
    }

    private void applyThinBorderColor(XSSFCellStyle style, XSSFWorkbook wb, byte[] rgb) {
        XSSFColor color = new XSSFColor(new java.awt.Color(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF), null);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(color);
        style.setBottomBorderColor(color);
        style.setLeftBorderColor(color);
        style.setRightBorderColor(color);
    }

    // ── Cell helpers ──────────────────────────────────────────────

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void setCellBoolean(Row row, int col, boolean value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value ? "Yes" : "No");
        cell.setCellStyle(style);
    }

    private String formatDate(LocalDateTime dt) {
        return dt != null ? dt.format(DATE_FORMATTER) : "";
    }

    private String joinList(List<String> items) {
        if (items == null || items.isEmpty()) return "";
        return String.join(", ", items);
    }

    /**
     * Removes characters invalid in Excel sheet names and truncates to 31 chars.
     * Invalid chars: \ / ? * [ ] :
     */
    private String sanitizeSheetName(String name) {
        String sanitized = name.replaceAll("[\\\\/?*\\[\\]:]", "").trim();
        if (sanitized.isEmpty()) sanitized = "Unknown";
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }
}
