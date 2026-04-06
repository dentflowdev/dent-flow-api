package com.dentalManagement.dentalFlowBackend.service;

import com.dentalManagement.dentalFlowBackend.model.Order;
import com.dentalManagement.dentalFlowBackend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExportService {

    private final OrderRepository orderRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_DATE     = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT      = DateTimeFormatter.ofPattern("MMM yyyy");

    private static final String[] HEADERS = {
            "Barcode ID", "Case Number", "Box Number", "Patient Name", "Doctor Name",
            "Order Type", "Teeth", "Shade", "Materials", "Instructions",
            "Due Date", "Delivery Schedule", "Current Status", "Created At",
            "Delivered At", "Is Edited"
    };

    // ── Color Palette ─────────────────────────────────────────────────────
    private static final Color CORP_BLUE   = new Color(25,  55,  95);   // #193757
    private static final Color SOFT_NAVY   = new Color(41,  98,  161);  // #296EA1
    private static final Color WHITE       = new Color(255, 255, 255);
    private static final Color OFF_WHITE   = new Color(248, 250, 252);  // #F8FAFC
    private static final Color LIGHT_SLATE = new Color(235, 240, 245);  // #EBF0F5
    private static final Color TEXT_DARK   = new Color(45,  50,  60);   // #2D323C
    private static final Color BORDER_CLR  = new Color(200, 210, 220);  // #CAD2DC

    // ── Time grouping strategy ────────────────────────────────────────────
    private enum TimeGranularity { DAILY, WEEKLY, MONTHLY }

    private TimeGranularity resolveGranularity(long daysBetween) {
        if (daysBetween < 90)  return TimeGranularity.DAILY;
        if (daysBetween <= 365) return TimeGranularity.WEEKLY;
        return TimeGranularity.MONTHLY;
    }

    // ── Entry point ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ByteArrayOutputStream generateOrdersExcel(UUID labId, LocalDateTime startDate, LocalDateTime endDate)
            throws IOException {

        List<Order> orders = orderRepository.findByCreatedAtBetweenAndLabId(startDate, endDate, labId);
        log.info("Exporting {} orders for labId={} between {} and {}", orders.size(), labId, startDate, endDate);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            writeOrderSheet(wb, "All Orders", orders);

            Map<String, List<Order>> byDoctor = orders.stream()
                    .collect(Collectors.groupingBy(
                            o -> o.getDoctorName() != null ? o.getDoctorName() : "Unknown",
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
            byDoctor.forEach((doctor, doctorOrders) ->
                    writeOrderSheet(wb, sanitizeSheetName(doctor), doctorOrders));

            long daysBetween = ChronoUnit.DAYS.between(startDate.toLocalDate(), endDate.toLocalDate());
            TimeGranularity granularity = resolveGranularity(daysBetween);

            writeAnalyticsSheet(wb, orders, byDoctor, startDate, endDate, granularity);

            // Separate doctor breakdown sheet — only when there are many doctors
            if (byDoctor.size() > 20) {
                writeDoctorAnalyticsSheet(wb, byDoctor);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out;
        }
    }

    // ── Order data sheets ─────────────────────────────────────────────────

    private void writeOrderSheet(XSSFWorkbook wb, String sheetName, List<Order> orders) {
        XSSFSheet sheet = wb.createSheet(sheetName);
        sheet.createFreezePane(0, 1);
        sheet.setMargin(Sheet.LeftMargin,   0.5);
        sheet.setMargin(Sheet.RightMargin,  0.5);
        sheet.setMargin(Sheet.TopMargin,    0.75);
        sheet.setMargin(Sheet.BottomMargin, 0.75);

        XSSFCellStyle headerStyle = buildHeaderStyle(wb);
        XSSFCellStyle oddBase     = buildDataStyle(wb, OFF_WHITE,   false);
        XSSFCellStyle evenBase    = buildDataStyle(wb, LIGHT_SLATE, false);
        XSSFCellStyle oddCenter   = buildDataStyle(wb, OFF_WHITE,   true);
        XSSFCellStyle evenCenter  = buildDataStyle(wb, LIGHT_SLATE, true);
        XSSFCellStyle oddWrap     = buildWrapStyle(wb, OFF_WHITE);
        XSSFCellStyle evenWrap    = buildWrapStyle(wb, LIGHT_SLATE);

        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(22f);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < orders.size(); i++) {
            Order o = orders.get(i);
            boolean even = (i % 2 == 1);
            Row row = sheet.createRow(i + 1);
            row.setHeightInPoints(22f);

            XSSFCellStyle base   = even ? evenBase   : oddBase;
            XSSFCellStyle center = even ? evenCenter : oddCenter;
            XSSFCellStyle wrap   = even ? evenWrap   : oddWrap;

            setCell(row,  0, o.getBarcodeId(),             base);
            setCell(row,  1, o.getCaseNumber(),             base);
            setCell(row,  2, o.getBoxNumber(),              base);
            setCell(row,  3, o.getPatientName(),            base);
            setCell(row,  4, o.getDoctorName(),             base);
            setCell(row,  5, o.getOrderType(),              base);
            setCell(row,  6, joinList(o.getTeeth()),        wrap);
            setCell(row,  7, joinList(o.getShade()),        wrap);
            setCell(row,  8, joinList(o.getMaterials()),    wrap);
            setCell(row,  9, o.getInstructions(),           wrap);
            setCell(row, 10, formatDate(o.getDueDate()),    base);
            setCell(row, 11, o.getDeliverySchedule(),       base);
            setCell(row, 12, o.getCurrentStatus().name(),   base);
            setCell(row, 13, formatDate(o.getCreatedAt()),  base);
            setCell(row, 14, formatDate(o.getDeliveredAt()), base);
            setCellBool(row, 15, o.isEdited(),              center);
        }

        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.max(15 * 256, Math.min(w, 50 * 256)));
        }
    }

    // ── Analytics Dashboard sheet ─────────────────────────────────────────

    private void writeAnalyticsSheet(XSSFWorkbook wb, List<Order> orders,
                                     Map<String, List<Order>> byDoctor,
                                     LocalDateTime startDate, LocalDateTime endDate,
                                     TimeGranularity granularity) {
        XSSFSheet sheet = wb.createSheet("Analytics Dashboard");

        // Summary — rows 0 to N (dynamic height)
        int nextRow = writeSummarySection(wb, sheet, orders, byDoctor, startDate, endDate, granularity);

        // Charts begin at row 10 minimum, side by side
        int chartStart = Math.max(nextRow + 1, 10);
        int chartEnd   = chartStart + 24;

        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        writePieChart(drawing, byDoctor, chartStart, chartEnd);
        writeBarChart(drawing, orders, granularity, chartStart, chartEnd);

        // Inline doctor table below charts (when doctors > 10 and no separate sheet)
        if (byDoctor.size() > 10) {
            writeDoctorTable(wb, sheet, byDoctor, chartEnd + 2);
        }

        // Column widths: summary labels | pie area (cols 0-7) | bar area (cols 8-16)
        sheet.setColumnWidth(0, 2  * 256);
        sheet.setColumnWidth(1, 22 * 256);
        for (int i = 2; i <= 7;  i++) sheet.setColumnWidth(i, 11 * 256);
        for (int i = 8; i <= 16; i++) sheet.setColumnWidth(i, 10 * 256);
    }

    // ── Summary section ───────────────────────────────────────────────────

    private int writeSummarySection(XSSFWorkbook wb, XSSFSheet sheet, List<Order> orders,
                                    Map<String, List<Order>> byDoctor,
                                    LocalDateTime startDate, LocalDateTime endDate,
                                    TimeGranularity granularity) {

        XSSFCellStyle titleStyle   = buildTitleStyle(wb);
        XSSFCellStyle sectionStyle = buildSectionStyle(wb);
        XSSFCellStyle labelStyle   = buildLabelStyle(wb);
        XSSFCellStyle valueStyle   = buildValueStyle(wb);

        // Title row
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(36f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Analytics Dashboard");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 16));

        // Section header
        Row sectionRow = sheet.createRow(2);
        sectionRow.setHeightInPoints(22f);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue("Summary");
        sectionCell.setCellStyle(sectionStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 16));

        // Compute metrics
        int doctorCount = byDoctor.size();

        Optional<Map.Entry<String, List<Order>>> mostActive = byDoctor.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()));

        String chartNote = switch (granularity) {
            case DAILY   -> "Daily bars  (range < 90 days)";
            case WEEKLY  -> "Weekly bars  (range 90–365 days)";
            case MONTHLY -> "Monthly bars  (range > 1 year)";
        };

        // Doctor summary: list all if ≤ 10, else top 3 + overflow count
        String doctorSummary = doctorCount <= 10
                ? byDoctor.entrySet().stream()
                        .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                        .map(e -> e.getKey() + " (" + e.getValue().size() + ")")
                        .collect(Collectors.joining(", "))
                : byDoctor.entrySet().stream()
                        .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                        .limit(3)
                        .map(e -> e.getKey() + " (" + e.getValue().size() + ")")
                        .collect(Collectors.joining(", "))
                        + "   +  " + (doctorCount - 3) + " more";

        // Write rows
        int r = 3;
        r = writeSummaryRow(sheet, r, "Total Orders",
                String.valueOf(orders.size()), labelStyle, valueStyle);
        r = writeSummaryRow(sheet, r, "Total Doctors",
                String.valueOf(doctorCount), labelStyle, valueStyle);
        r = writeSummaryRow(sheet, r, "Date Range",
                startDate.format(SHORT_DATE) + "   →   " + endDate.format(SHORT_DATE),
                labelStyle, valueStyle);
        r = writeSummaryRow(sheet, r, "Most Active Doctor",
                mostActive.map(e -> e.getKey() + "  (" + e.getValue().size() + " orders)").orElse("N/A"),
                labelStyle, valueStyle);
        r = writeSummaryRow(sheet, r, doctorCount <= 10 ? "All Doctors" : "Top Doctors",
                doctorSummary, labelStyle, valueStyle);
        r = writeSummaryRow(sheet, r, "Chart Grouping",
                chartNote, labelStyle, valueStyle);
        if (doctorCount > 20) {
            r = writeSummaryRow(sheet, r, "Full Breakdown",
                    "See  'Doctor Analytics'  sheet", labelStyle, valueStyle);
        }
        return r;
    }

    private int writeSummaryRow(XSSFSheet sheet, int rowIdx, String label, String value,
                                XSSFCellStyle labelStyle, XSSFCellStyle valueStyle) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(20f);
        Cell lbl = row.createCell(1);
        lbl.setCellValue(label);
        lbl.setCellStyle(labelStyle);
        Cell val = row.createCell(2);
        val.setCellValue(value);
        val.setCellStyle(valueStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 2, 7));
        return rowIdx + 1;
    }

    // ── Pie chart: Top 10 doctors + "Others" ─────────────────────────────

    private void writePieChart(XSSFDrawing drawing, Map<String, List<Order>> byDoctor,
                               int startRow, int endRow) {
        if (byDoctor.isEmpty()) return;

        // Sort desc, cap at 10, accumulate remainder into "Others"
        List<Map.Entry<String, Integer>> ranked = byDoctor.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().size()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        int topN = Math.min(10, ranked.size());
        List<String> names  = new ArrayList<>();
        List<Double> counts = new ArrayList<>();

        for (int i = 0; i < topN; i++) {
            names.add(ranked.get(i).getKey());
            counts.add((double) ranked.get(i).getValue());
        }
        if (ranked.size() > 10) {
            long othersSum = ranked.subList(10, ranked.size()).stream()
                    .mapToLong(Map.Entry::getValue).sum();
            names.add("Others (" + (ranked.size() - 10) + ")");
            counts.add((double) othersSum);
        }

        String title = ranked.size() > 10
                ? "Top 10 Doctors  (" + byDoctor.size() + " total)"
                : "Orders by Doctor";

        // Anchor: cols 0–7 (left half)
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, startRow, 8, endRow);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFDataSource<String>         categories = XDDFDataSourcesFactory.fromArray(names.toArray(new String[0]));
        XDDFNumericalDataSource<Double> values    = XDDFDataSourcesFactory.fromArray(counts.toArray(new Double[0]));

        XDDFPieChartData pie = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);
        pie.setVaryColors(true);
        XDDFPieChartData.Series series = (XDDFPieChartData.Series) pie.addSeries(categories, values);
        series.setTitle("Orders", null);
        chart.plot(pie);
    }

    // ── Bar chart: Smart time grouping ────────────────────────────────────

    private void writeBarChart(XSSFDrawing drawing, List<Order> orders,
                               TimeGranularity granularity, int startRow, int endRow) {
        Map<String, Long> grouped = groupByTime(orders, granularity);
        if (grouped.isEmpty()) return;

        // Anchor: cols 8–16 (right half, side-by-side with pie)
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 8, startRow, 17, endRow);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(barChartTitle(granularity));
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis    yAxis = chart.createValueAxis(AxisPosition.LEFT);
        yAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        String[] labels = grouped.keySet().toArray(new String[0]);
        Double[] values = grouped.values().stream().map(Long::doubleValue).toArray(Double[]::new);

        XDDFDataSource<String>         categories = XDDFDataSourcesFactory.fromArray(labels);
        XDDFNumericalDataSource<Double> data      = XDDFDataSourcesFactory.fromArray(values);

        XDDFBarChartData bar = (XDDFBarChartData) chart.createData(ChartTypes.BAR, xAxis, yAxis);
        bar.setBarDirection(BarDirection.COL);
        XDDFBarChartData.Series series = (XDDFBarChartData.Series) bar.addSeries(categories, data);
        series.setTitle(barChartTitle(granularity), null);
        chart.plot(bar);
    }

    /**
     * Returns a chronologically-ordered map of period-label → order count.
     * Granularity is chosen based on the date range of the export.
     */
    private Map<String, Long> groupByTime(List<Order> orders, TimeGranularity granularity) {
        return switch (granularity) {
            case DAILY -> orders.stream()
                    .filter(o -> o.getCreatedAt() != null)
                    .sorted(Comparator.comparing(Order::getCreatedAt))
                    .collect(Collectors.groupingBy(
                            o -> o.getCreatedAt().toLocalDate().format(SHORT_DATE),
                            LinkedHashMap::new, Collectors.counting()));

            case WEEKLY -> orders.stream()
                    .filter(o -> o.getCreatedAt() != null)
                    .sorted(Comparator.comparing(Order::getCreatedAt))
                    .collect(Collectors.groupingBy(o -> {
                        LocalDate d     = o.getCreatedAt().toLocalDate();
                        int yr          = d.get(IsoFields.WEEK_BASED_YEAR);
                        int wk          = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                        LocalDate start = d.with(DayOfWeek.MONDAY);
                        return String.format("%d-W%02d (%s)", yr, wk, start.format(SHORT_DATE));
                    }, LinkedHashMap::new, Collectors.counting()));

            case MONTHLY -> orders.stream()
                    .filter(o -> o.getCreatedAt() != null)
                    .sorted(Comparator.comparing(Order::getCreatedAt))
                    .collect(Collectors.groupingBy(
                            o -> YearMonth.from(o.getCreatedAt()).format(MONTH_FMT),
                            LinkedHashMap::new, Collectors.counting()));
        };
    }

    private String barChartTitle(TimeGranularity g) {
        return switch (g) {
            case DAILY   -> "Orders per Day";
            case WEEKLY  -> "Orders per Week";
            case MONTHLY -> "Orders per Month";
        };
    }

    // ── Inline doctor table (analytics sheet, when doctors > 10) ─────────

    private void writeDoctorTable(XSSFWorkbook wb, XSSFSheet sheet,
                                  Map<String, List<Order>> byDoctor, int startRow) {
        XSSFCellStyle sectionStyle = buildSectionStyle(wb);
        XSSFCellStyle hdrStyle     = buildHeaderStyle(wb);
        XSSFCellStyle oddBase      = buildDataStyle(wb, OFF_WHITE,   false);
        XSSFCellStyle evenBase     = buildDataStyle(wb, LIGHT_SLATE, false);
        XSSFCellStyle oddNum       = buildDataStyle(wb, OFF_WHITE,   true);
        XSSFCellStyle evenNum      = buildDataStyle(wb, LIGHT_SLATE, true);

        // Section header
        Row sectionRow = sheet.createRow(startRow);
        sectionRow.setHeightInPoints(22f);
        Cell sec = sectionRow.createCell(0);
        sec.setCellValue("All Doctors — Order Counts");
        sec.setCellStyle(sectionStyle);
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow, 0, 5));

        // Column headers
        Row hdrRow = sheet.createRow(startRow + 1);
        hdrRow.setHeightInPoints(20f);
        createCell(hdrRow, 1, "Doctor Name", hdrStyle);
        createCell(hdrRow, 2, "Order Count", hdrStyle);
        createCell(hdrRow, 3, "% of Total",  hdrStyle);

        int total = byDoctor.values().stream().mapToInt(List::size).sum();
        List<Map.Entry<String, List<Order>>> ranked = byDoctor.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .collect(Collectors.toList());

        for (int i = 0; i < ranked.size(); i++) {
            Map.Entry<String, List<Order>> entry = ranked.get(i);
            boolean even = (i % 2 == 1);
            Row row = sheet.createRow(startRow + 2 + i);
            row.setHeightInPoints(18f);

            int count = entry.getValue().size();
            double pct = total > 0 ? (count * 100.0 / total) : 0;

            setCell(row, 1, entry.getKey(),                  even ? evenBase : oddBase);
            setNumericCell(row, 2, count,                    even ? evenNum  : oddNum);
            setCell(row, 3, String.format("%.1f%%", pct),   even ? evenNum  : oddNum);
        }
    }

    // ── Doctor Analytics sheet (only when doctors > 20) ──────────────────

    private void writeDoctorAnalyticsSheet(XSSFWorkbook wb, Map<String, List<Order>> byDoctor) {
        XSSFSheet sheet = wb.createSheet("Doctor Analytics");
        sheet.createFreezePane(0, 2);

        XSSFCellStyle titleStyle = buildTitleStyle(wb);
        XSSFCellStyle hdrStyle   = buildHeaderStyle(wb);
        XSSFCellStyle oddBase    = buildDataStyle(wb, OFF_WHITE,   false);
        XSSFCellStyle evenBase   = buildDataStyle(wb, LIGHT_SLATE, false);
        XSSFCellStyle oddNum     = buildDataStyle(wb, OFF_WHITE,   true);
        XSSFCellStyle evenNum    = buildDataStyle(wb, LIGHT_SLATE, true);

        // Title
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("All Doctors Analytics  (" + byDoctor.size() + " doctors)");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        // Column headers
        Row hdrRow = sheet.createRow(1);
        hdrRow.setHeightInPoints(22f);
        createCell(hdrRow, 0, "#",            hdrStyle);
        createCell(hdrRow, 1, "Doctor Name",  hdrStyle);
        createCell(hdrRow, 2, "Order Count",  hdrStyle);
        createCell(hdrRow, 3, "% of Total",   hdrStyle);

        int total = byDoctor.values().stream().mapToInt(List::size).sum();
        List<Map.Entry<String, List<Order>>> ranked = byDoctor.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .collect(Collectors.toList());

        for (int i = 0; i < ranked.size(); i++) {
            Map.Entry<String, List<Order>> entry = ranked.get(i);
            boolean even = (i % 2 == 1);
            Row row = sheet.createRow(i + 2);
            row.setHeightInPoints(18f);

            int count = entry.getValue().size();
            double pct = total > 0 ? (count * 100.0 / total) : 0;

            setNumericCell(row, 0, i + 1,                     even ? evenNum  : oddNum);
            setCell(row,        1, entry.getKey(),             even ? evenBase : oddBase);
            setNumericCell(row, 2, count,                      even ? evenNum  : oddNum);
            setCell(row,        3, String.format("%.1f%%", pct), even ? evenNum  : oddNum);
        }

        sheet.setColumnWidth(0, 6  * 256);
        sheet.setColumnWidth(1, 30 * 256);
        sheet.setColumnWidth(2, 14 * 256);
        sheet.setColumnWidth(3, 12 * 256);
    }

    // ── Style builders ────────────────────────────────────────────────────

    private XSSFCellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(xColor(CORP_BLUE));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true); font.setColor(xColor(WHITE));
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style, xColor(SOFT_NAVY), BorderStyle.MEDIUM);
        return style;
    }

    private XSSFCellStyle buildDataStyle(XSSFWorkbook wb, Color bg, boolean center) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(xColor(bg));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
        font.setColor(xColor(TEXT_DARK));
        style.setFont(font);
        style.setAlignment(center ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style, xColor(BORDER_CLR), BorderStyle.THIN);
        style.setWrapText(false);
        return style;
    }

    private XSSFCellStyle buildWrapStyle(XSSFWorkbook wb, Color bg) {
        XSSFCellStyle style = buildDataStyle(wb, bg, false);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private XSSFCellStyle buildTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(xColor(CORP_BLUE));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true); font.setColor(xColor(WHITE));
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 18);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle buildSectionStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(xColor(SOFT_NAVY));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true); font.setColor(xColor(WHITE));
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle buildLabelStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(xColor(LIGHT_SLATE));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true); font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10); font.setColor(xColor(TEXT_DARK));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style, xColor(BORDER_CLR), BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle buildValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(xColor(OFF_WHITE));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setFontName("Arial"); font.setFontHeightInPoints((short) 10);
        font.setColor(xColor(TEXT_DARK));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style, xColor(BORDER_CLR), BorderStyle.THIN);
        return style;
    }

    private void applyBorders(XSSFCellStyle style, XSSFColor color, BorderStyle bs) {
        style.setBorderTop(bs);    style.setTopBorderColor(color);
        style.setBorderBottom(bs); style.setBottomBorderColor(color);
        style.setBorderLeft(bs);   style.setLeftBorderColor(color);
        style.setBorderRight(bs);  style.setRightBorderColor(color);
    }

    // ── Cell helpers ──────────────────────────────────────────────────────

    private Cell createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void setNumericCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setCellBool(Row row, int col, boolean value, CellStyle style) {
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

    private String sanitizeSheetName(String name) {
        String s = name.replaceAll("[\\\\/?*\\[\\]:]", "").trim();
        if (s.isEmpty()) s = "Unknown";
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    private XSSFColor xColor(Color c) {
        return new XSSFColor(c, null);
    }
}
