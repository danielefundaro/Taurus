package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinanceReportService {

    private static final DateTimeFormatter ITALIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final FinancialMovementRepository movementRepository;

    public FinanceReportService(FinancialMovementRepository movementRepository) {
        this.movementRepository = movementRepository;
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
        String tenant = requiredTenant(token);
        LocalDate effectiveTo = to == null ? LocalDate.now() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfYear(1) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw error(HttpStatus.BAD_REQUEST, "La data iniziale deve precedere la data finale", "finance.report.invalidPeriod");
        }
        List<FinancialMovement> movements = movementRepository.findAllByDeletedFalseAndBookingDateBetween(effectiveFrom, effectiveTo).stream()
            .filter(movement -> accountId == null || movement.getAccount().getId().equals(accountId))
            .filter(movement -> categoryId == null || (movement.getCategory() != null && movement.getCategory().getId().equals(categoryId)))
            .filter(movement -> eventId == null || (movement.getEvent() != null && movement.getEvent().getId().equals(eventId)))
            .sorted((left, right) -> {
                int date = left.getBookingDate().compareTo(right.getBookingDate());
                return date != 0 ? date : left.getId().compareTo(right.getId());
            })
            .toList();
        String normalized = format == null ? "csv" : format.toLowerCase(Locale.ROOT);
        String baseName = "registro-cassa-" + effectiveFrom + "-" + effectiveTo;
        ReportContent report = switch (normalized) {
            case "csv" -> new ReportContent(baseName + ".csv", "text/csv;charset=UTF-8", csv(tenant, effectiveFrom, effectiveTo, movements));
            case "xlsx" -> new ReportContent(baseName + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(tenant, effectiveFrom, effectiveTo, movements));
            case "pdf" -> new ReportContent(baseName + ".pdf", "application/pdf", pdf(tenant, effectiveFrom, effectiveTo, movements));
            default -> throw error(HttpStatus.BAD_REQUEST, "Formato di esportazione non supportato", "finance.report.unsupportedFormat");
        };
        return report;
    }

    private byte[] csv(String tenant, LocalDate from, LocalDate to, List<FinancialMovement> movements) {
        StringBuilder content = new StringBuilder("\uFEFF");
        content.append("Tenant;").append(csvValue(tenant)).append('\n');
        content.append("Periodo;").append(from.format(ITALIAN_DATE)).append(" - ").append(to.format(ITALIAN_DATE)).append("\n\n");
        content.append("Data;Conto;Direzione;Natura;Categoria;Evento;Descrizione;Controparte;Riferimento;Importo;Valuta;Riconciliato\n");
        for (FinancialMovement movement : movements) {
            content.append(movement.getBookingDate().format(ITALIAN_DATE)).append(';')
                .append(csvValue(movement.getAccount().getName())).append(';')
                .append(movement.getDirection()).append(';')
                .append(movement.getNature()).append(';')
                .append(csvValue(movement.getCategory() == null ? null : movement.getCategory().getName())).append(';')
                .append(csvValue(movement.getEventNameSnapshot())).append(';')
                .append(csvValue(movement.getDescription())).append(';')
                .append(csvValue(movement.getCounterparty())).append(';')
                .append(csvValue(movement.getDocumentReference())).append(';')
                .append(signed(movement).toPlainString().replace('.', ',')).append(';')
                .append(movement.getCurrency()).append(';')
                .append(movement.isReconciled() ? "Sì" : "No").append('\n');
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(String tenant, LocalDate from, LocalDate to, List<FinancialMovement> movements) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Registro cassa");
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Registro cassa - " + tenant);
            title.createCell(1).setCellValue(from.format(ITALIAN_DATE) + " - " + to.format(ITALIAN_DATE));
            Row header = sheet.createRow(2);
            String[] labels = { "Data", "Conto", "Direzione", "Natura", "Categoria", "Evento", "Descrizione", "Controparte", "Riferimento", "Importo", "Valuta", "Riconciliato" };
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            for (int index = 0; index < labels.length; index++) {
                header.createCell(index).setCellValue(labels[index]);
                header.getCell(index).setCellStyle(headerStyle);
            }
            DataFormat dataFormat = workbook.createDataFormat();
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));
            int rowIndex = 3;
            for (FinancialMovement movement : movements) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(movement.getBookingDate().format(ITALIAN_DATE));
                row.createCell(1).setCellValue(movement.getAccount().getName());
                row.createCell(2).setCellValue(movement.getDirection().name());
                row.createCell(3).setCellValue(movement.getNature().name());
                row.createCell(4).setCellValue(movement.getCategory() == null ? "" : movement.getCategory().getName());
                row.createCell(5).setCellValue(value(movement.getEventNameSnapshot()));
                row.createCell(6).setCellValue(movement.getDescription());
                row.createCell(7).setCellValue(value(movement.getCounterparty()));
                row.createCell(8).setCellValue(value(movement.getDocumentReference()));
                row.createCell(9).setCellValue(signed(movement).doubleValue());
                row.getCell(9).setCellStyle(moneyStyle);
                row.createCell(10).setCellValue(movement.getCurrency());
                row.createCell(11).setCellValue(movement.isReconciled() ? "Sì" : "No");
            }
            for (int index = 0; index < labels.length; index++) sheet.autoSizeColumn(index);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile generare il file XLSX", "finance.report.generationFailed");
        }
    }

    private byte[] pdf(String tenant, LocalDate from, LocalDate to, List<FinancialMovement> movements) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            List<String> lines = new ArrayList<>();
            lines.add("Registro cassa - " + tenant);
            lines.add("Periodo: " + from.format(ITALIAN_DATE) + " - " + to.format(ITALIAN_DATE));
            lines.add("");
            for (FinancialMovement movement : movements) {
                lines.add(
                    movement.getBookingDate().format(ITALIAN_DATE) + " | " + movement.getAccount().getName() + " | " +
                    movement.getDescription() + " | " + signed(movement).setScale(2, RoundingMode.HALF_UP) + " " + movement.getCurrency()
                );
            }
            int index = 0;
            while (index < lines.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setLeading(15);
                    stream.newLineAtOffset(45, 800);
                    int pageLine = 0;
                    while (index < lines.size() && pageLine < 48) {
                        stream.setFont(index == 0 ? bold : regular, index == 0 ? 14 : 9);
                        stream.showText(pdfSafe(lines.get(index)));
                        stream.newLine();
                        index++;
                        pageLine++;
                    }
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile generare il PDF", "finance.report.generationFailed");
        }
    }

    private static BigDecimal signed(FinancialMovement movement) {
        return movement.getDirection() == FinancialDirection.INCOME ? movement.getAmount() : movement.getAmount().negate();
    }

    private static String csvValue(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }

    private static String pdfSafe(String value) {
        return value.replace('€', 'E').replaceAll("[^\\x20-\\x7EÀ-ÿ]", "?");
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static String requiredTenant(AbstractAuthenticationToken token) {
        String tenant = SecurityUtils.getTenantIdFromAuthentication(token);
        if (tenant == null || tenant.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Tenant non disponibile", "finance.tenant.missing");
        return tenant;
    }

    private static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, "finance", key);
    }

    public record ReportContent(String fileName, String mimeType, byte[] bytes) {}
}
