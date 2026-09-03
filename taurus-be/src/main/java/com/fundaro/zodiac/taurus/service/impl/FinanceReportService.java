package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountStatementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountYearBalanceDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryTotalDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventEconomicLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.StatementLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearSummaryDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rendiconti economici. Ogni esportazione è generata dal backend a partire dagli
 * stessi calcoli usati dalle API, così CSV, XLSX e PDF non possono divergere dai
 * totali mostrati a video, e riporta sempre tenant, periodo, filtri applicati,
 * data di generazione e utente richiedente.
 */
@Service
@Transactional(readOnly = true)
public class FinanceReportService {

    private static final DateTimeFormatter ITALIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ITALIAN_TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int PDF_LINES_PER_PAGE = 48;

    private final FinancialMovementRepository movementRepository;
    private final FinanceService financeService;

    public FinanceReportService(FinancialMovementRepository movementRepository, FinanceService financeService) {
        this.movementRepository = movementRepository;
        this.financeService = financeService;
    }

    @Transactional
    public ReportContent cashbook(
        LocalDate from,
        LocalDate to,
        Long accountId,
        Long categoryId,
        Long eventId,
        String format,
        AbstractAuthenticationToken token
    ) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfYear(1) : from;
        requirePeriod(effectiveFrom, effectiveTo);
        List<FinancialMovement> movements = movementRepository
            .findAllByDeletedFalseAndBookingDateBetween(effectiveFrom, effectiveTo)
            .stream()
            .filter(movement -> accountId == null || movement.getAccount().getId().equals(accountId))
            .filter(movement -> categoryId == null || (movement.getCategory() != null && movement.getCategory().getId().equals(categoryId)))
            .filter(movement -> eventId == null || (movement.getEvent() != null && movement.getEvent().getId().equals(eventId)))
            .sorted((left, right) -> {
                int date = left.getBookingDate().compareTo(right.getBookingDate());
                return date != 0 ? date : left.getId().compareTo(right.getId());
            })
            .toList();

        List<String> filters = new ArrayList<>();
        if (accountId != null) filters.add("Conto id " + accountId);
        if (categoryId != null) filters.add("Categoria id " + categoryId);
        if (eventId != null) filters.add("Evento id " + eventId);

        List<Object[]> rows = new ArrayList<>();
        for (FinancialMovement movement : movements) {
            rows.add(
                new Object[] {
                    movement.getBookingDate(),
                    movement.getAccount().getName(),
                    movement.getDirection().name(),
                    movement.getNature().name(),
                    movement.getCategory() == null ? null : movement.getCategory().getName(),
                    movement.getEventNameSnapshot(),
                    movement.getDescription(),
                    movement.getCounterparty(),
                    movement.getDocumentReference(),
                    signed(movement),
                    movement.getCurrency(),
                    movement.isReconciled()
                }
            );
        }

        Section section = new Section(
            "Movimenti",
            List.of("Data", "Conto", "Direzione", "Natura", "Categoria", "Evento", "Descrizione", "Controparte", "Riferimento", "Importo", "Valuta", "Riconciliato"),
            rows
        );
        Meta meta = meta("Registro cassa", effectiveFrom, effectiveTo, filters, token);
        return render("registro-cassa-" + effectiveFrom + "-" + effectiveTo, meta, List.of(section), format);
    }

    @Transactional
    public ReportContent accountStatement(long accountId, LocalDate from, LocalDate to, String format, AbstractAuthenticationToken token) {
        AccountStatementDTO statement = financeService.accountStatement(accountId, from, to, token);

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] { statement.from(), "Saldo iniziale", null, null, null, null, statement.openingBalance() });
        for (StatementLineDTO line : statement.lines()) {
            MovementDTO movement = line.movement();
            rows.add(
                new Object[] {
                    movement.bookingDate(),
                    movement.description(),
                    movement.categoryName(),
                    movement.eventName(),
                    movement.direction() == FinancialDirection.INCOME ? movement.amount() : null,
                    movement.direction() == FinancialDirection.EXPENSE ? movement.amount() : null,
                    line.balance()
                }
            );
        }
        rows.add(new Object[] { statement.to(), "Saldo finale", null, null, statement.income(), statement.expense(), statement.closingBalance() });

        Section section = new Section("Estratto conto", List.of("Data", "Descrizione", "Categoria", "Evento", "Entrate", "Uscite", "Saldo"), rows);
        Meta meta = meta(
            "Estratto conto - " + statement.account().name(),
            statement.from(),
            statement.to(),
            List.of("Conto: " + statement.account().name()),
            token
        );
        return render("estratto-conto-" + accountId + "-" + statement.from() + "-" + statement.to(), meta, List.of(section), format);
    }

    @Transactional
    public ReportContent events(LocalDate from, LocalDate to, String format, AbstractAuthenticationToken token) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfYear(1) : from;
        List<EventEconomicLineDTO> lines = financeService.eventLines(effectiveFrom, effectiveTo, token);
        Section section = new Section("Eventi", eventHeaders(), eventRows(lines));
        Meta meta = meta("Rendiconto per evento", effectiveFrom, effectiveTo, List.of(), token);
        return render("rendiconto-eventi-" + effectiveFrom + "-" + effectiveTo, meta, List.of(section), format);
    }

    @Transactional
    public ReportContent categories(LocalDate from, LocalDate to, String format, AbstractAuthenticationToken token) {
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfYear(1) : from;
        List<CategoryTotalDTO> totals = financeService.categoryTotals(effectiveFrom, effectiveTo, token);
        Section section = new Section("Categorie", categoryHeaders(), categoryRows(totals));
        Meta meta = meta("Rendiconto per categoria", effectiveFrom, effectiveTo, List.of(), token);
        return render("rendiconto-categorie-" + effectiveFrom + "-" + effectiveTo, meta, List.of(section), format);
    }

    @Transactional
    public ReportContent annual(int year, String format, AbstractAuthenticationToken token) {
        YearSummaryDTO summary = financeService.yearSummary(year, token);

        List<Object[]> accountRows = new ArrayList<>();
        for (AccountYearBalanceDTO account : summary.accounts()) {
            accountRows.add(
                new Object[] { account.accountName(), account.openingBalance(), account.income(), account.expense(), account.closingBalance() }
            );
        }
        Section accounts = new Section("Conti", List.of("Conto", "Saldo iniziale", "Entrate", "Uscite", "Saldo finale"), accountRows);

        List<Object[]> totalRows = new ArrayList<>();
        totalRows.add(new Object[] { "Saldo iniziale complessivo", summary.openingTotal() });
        totalRows.add(new Object[] { "Entrate ordinarie", summary.ordinaryIncome() });
        totalRows.add(new Object[] { "Uscite ordinarie", summary.ordinaryExpense() });
        totalRows.add(new Object[] { "Risultato ordinario", summary.ordinaryResult() });
        totalRows.add(new Object[] { "Trasferimenti tra conti", summary.transferTotal() });
        totalRows.add(new Object[] { "Saldo finale complessivo", summary.closingTotal() });
        totalRows.add(new Object[] { "Movimenti non riconciliati", BigDecimal.valueOf(summary.unreconciledCount()) });
        totalRows.add(new Object[] { "Importo non riconciliato", summary.unreconciledAmount() });
        totalRows.add(
            new Object[] {
                "Ultimo ricalcolo dei riporti",
                summary.lastRecalculatedAt() == null ? "mai" : summary.lastRecalculatedAt().format(ITALIAN_TIMESTAMP)
            }
        );
        Section totals = new Section("Totali", List.of("Voce", "Valore"), totalRows);

        Section categories = new Section("Categorie", categoryHeaders(), categoryRows(summary.categories()));
        Section openEvents = new Section("Eventi aperti", eventHeaders(), eventRows(summary.openEvents()));

        Meta meta = meta(
            "Rendiconto annuale " + year,
            summary.year().startDate(),
            summary.year().endDate(),
            List.of("Esercizio: " + year + " (" + summary.year().status() + ")"),
            token
        );
        return render("rendiconto-annuale-" + year, meta, List.of(accounts, totals, categories, openEvents), format);
    }

    private static List<String> eventHeaders() {
        return List.of(
            "Evento",
            "Data",
            "Compenso previsto",
            "Costi previsti",
            "Margine previsto",
            "Incassato",
            "Pagato",
            "Risultato",
            "Residuo entrate",
            "Residuo uscite",
            "Stato"
        );
    }

    private static List<Object[]> eventRows(List<EventEconomicLineDTO> lines) {
        List<Object[]> rows = new ArrayList<>();
        for (EventEconomicLineDTO line : lines) {
            rows.add(
                new Object[] {
                    line.eventName(),
                    line.eventDate(),
                    line.expectedFee(),
                    line.expectedCosts(),
                    line.expectedMargin(),
                    line.received(),
                    line.paid(),
                    line.actualResult(),
                    line.remainingIncome(),
                    line.remainingExpense(),
                    economicStatusLabel(line.economicStatus())
                }
            );
        }
        return rows;
    }

    private static List<String> categoryHeaders() {
        return List.of("Categoria", "Direzione", "Entrate", "Uscite", "Netto", "Movimenti");
    }

    private static List<Object[]> categoryRows(List<CategoryTotalDTO> totals) {
        List<Object[]> rows = new ArrayList<>();
        for (CategoryTotalDTO total : totals) {
            rows.add(
                new Object[] {
                    total.categoryName(),
                    total.direction() == null ? "" : total.direction().name(),
                    total.income(),
                    total.expense(),
                    total.net(),
                    BigDecimal.valueOf(total.movementCount())
                }
            );
        }
        return rows;
    }

    private static String economicStatusLabel(String status) {
        return switch (status) {
            case "NO_BUDGET" -> "Nessun preventivo";
            case "NO_MOVEMENTS" -> "Nessun movimento";
            case "UNPLANNED_MOVEMENTS" -> "Movimenti senza preventivo";
            case "PARTIALLY_SETTLED" -> "Parzialmente saldato";
            case "OVERPAID_OR_OVERRUN" -> "Scostamento";
            case "SETTLED" -> "Saldato";
            default -> status;
        };
    }

    private Meta meta(String title, LocalDate from, LocalDate to, List<String> filters, AbstractAuthenticationToken token) {
        return new Meta(title, requiredTenant(token), from, to, filters, requester(token), ZonedDateTime.now());
    }

    private ReportContent render(String baseName, Meta meta, List<Section> sections, String format) {
        String normalized = format == null ? "csv" : format.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "csv" -> new ReportContent(baseName + ".csv", "text/csv;charset=UTF-8", csv(meta, sections));
            case "xlsx" -> new ReportContent(
                baseName + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx(meta, sections)
            );
            case "pdf" -> new ReportContent(baseName + ".pdf", "application/pdf", pdf(meta, sections));
            default -> throw error(HttpStatus.BAD_REQUEST, "Formato di esportazione non supportato", "finance.report.unsupportedFormat");
        };
    }

    private byte[] csv(Meta meta, List<Section> sections) {
        StringBuilder content = new StringBuilder("\uFEFF");
        content.append("Rendiconto;").append(csvValue(meta.title())).append('\n');
        content.append("Tenant;").append(csvValue(meta.tenant())).append('\n');
        content.append("Periodo;").append(csvValue(meta.periodLabel())).append('\n');
        content.append("Filtri;").append(csvValue(meta.filtersLabel())).append('\n');
        content.append("Generato il;").append(csvValue(meta.generatedAt().format(ITALIAN_TIMESTAMP))).append('\n');
        content.append("Richiesto da;").append(csvValue(meta.requestedBy())).append('\n');
        for (Section section : sections) {
            content.append('\n').append(csvValue(section.title())).append('\n');
            content.append(String.join(";", section.headers().stream().map(FinanceReportService::csvValue).toList())).append('\n');
            for (Object[] row : section.rows()) {
                List<String> cells = new ArrayList<>();
                for (Object cell : row) cells.add(cell instanceof BigDecimal amount ? decimal(amount) : csvValue(text(cell)));
                content.append(String.join(";", cells)).append('\n');
            }
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(Meta meta, List<Section> sections) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

            for (Section section : sections) {
                Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(sheetName(workbook, section.title())));
                Row title = sheet.createRow(0);
                title.createCell(0).setCellValue(meta.title() + " - " + meta.tenant());
                title.getCell(0).setCellStyle(headerStyle);
                sheet.createRow(1).createCell(0).setCellValue("Periodo: " + meta.periodLabel());
                sheet.createRow(2).createCell(0).setCellValue("Filtri: " + meta.filtersLabel());
                sheet
                    .createRow(3)
                    .createCell(0)
                    .setCellValue("Generato il " + meta.generatedAt().format(ITALIAN_TIMESTAMP) + " da " + meta.requestedBy());

                Row header = sheet.createRow(5);
                for (int index = 0; index < section.headers().size(); index++) {
                    header.createCell(index).setCellValue(section.headers().get(index));
                    header.getCell(index).setCellStyle(headerStyle);
                }
                int rowIndex = 6;
                for (Object[] values : section.rows()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int index = 0; index < values.length; index++) {
                        if (values[index] instanceof BigDecimal amount) {
                            row.createCell(index).setCellValue(amount.doubleValue());
                            row.getCell(index).setCellStyle(moneyStyle);
                        } else {
                            row.createCell(index).setCellValue(text(values[index]));
                        }
                    }
                }
                for (int index = 0; index < section.headers().size(); index++) sheet.autoSizeColumn(index);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile generare il file XLSX", "finance.report.generationFailed");
        }
    }

    private byte[] pdf(Meta meta, List<Section> sections) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            List<String> lines = new ArrayList<>();
            lines.add(meta.title() + " - " + meta.tenant());
            lines.add("Periodo: " + meta.periodLabel());
            lines.add("Filtri: " + meta.filtersLabel());
            lines.add("Generato il " + meta.generatedAt().format(ITALIAN_TIMESTAMP) + " da " + meta.requestedBy());
            for (Section section : sections) {
                lines.add("");
                lines.add(section.title());
                lines.add(String.join(" | ", section.headers()));
                for (Object[] row : section.rows()) {
                    List<String> cells = new ArrayList<>();
                    for (Object cell : row) cells.add(text(cell));
                    lines.add(String.join(" | ", cells));
                }
            }

            int index = 0;
            boolean firstPage = true;
            while (index < lines.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setLeading(15);
                    stream.newLineAtOffset(45, 800);
                    int pageLine = 0;
                    while (index < lines.size() && pageLine < PDF_LINES_PER_PAGE) {
                        boolean isTitle = firstPage && index == 0;
                        stream.setFont(isTitle ? bold : regular, isTitle ? 14 : 9);
                        stream.showText(pdfSafe(lines.get(index)));
                        stream.newLine();
                        index++;
                        pageLine++;
                    }
                    stream.endText();
                }
                firstPage = false;
            }
            if (document.getNumberOfPages() == 0) document.addPage(new PDPage(PDRectangle.A4));
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile generare il PDF", "finance.report.generationFailed");
        }
    }

    private static String sheetName(XSSFWorkbook workbook, String title) {
        String candidate = title.length() > 28 ? title.substring(0, 28) : title;
        String name = candidate;
        int suffix = 2;
        while (workbook.getSheet(name) != null) name = candidate + " " + suffix++;
        return name;
    }

    private static BigDecimal signed(FinancialMovement movement) {
        return movement.getDirection() == FinancialDirection.INCOME ? movement.getAmount() : movement.getAmount().negate();
    }

    private static String text(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDate date) return date.format(ITALIAN_DATE);
        if (value instanceof BigDecimal amount) return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        if (value instanceof Boolean flag) return flag ? "Sì" : "No";
        return value.toString();
    }

    private static String decimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String csvValue(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }

    private static String pdfSafe(String value) {
        return value.replace('€', 'E').replaceAll("[^\\x20-\\x7EÀ-ÿ]", "?");
    }

    private static void requirePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw error(HttpStatus.BAD_REQUEST, "La data iniziale deve precedere la data finale", "finance.report.invalidPeriod");
        }
    }

    private static String requiredTenant(AbstractAuthenticationToken token) {
        String tenant = SecurityUtils.getTenantIdFromAuthentication(token);
        if (tenant == null || tenant.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Tenant non disponibile", "finance.tenant.missing");
        return tenant;
    }

    private static String requester(AbstractAuthenticationToken token) {
        String firstName = SecurityUtils.getFirstNameFromAuthentication(token);
        String lastName = SecurityUtils.getLastNameFromAuthentication(token);
        String displayName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        if (!displayName.isBlank()) return displayName;
        String userId = SecurityUtils.getUserIdFromAuthentication(token);
        return userId == null || userId.isBlank() ? "non disponibile" : userId;
    }

    private static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, "finance", key);
    }

    private record Section(String title, List<String> headers, List<Object[]> rows) {}

    private record Meta(
        String title,
        String tenant,
        LocalDate from,
        LocalDate to,
        List<String> filters,
        String requestedBy,
        ZonedDateTime generatedAt
    ) {
        private String periodLabel() {
            if (from == null || to == null) return "non applicabile";
            return from.format(ITALIAN_DATE) + " - " + to.format(ITALIAN_DATE);
        }

        private String filtersLabel() {
            return filters == null || filters.isEmpty() ? "nessuno" : String.join(", ", filters);
        }
    }

    public record ReportContent(String fileName, String mimeType, byte[] bytes) {}
}
