package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportFormat;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingSection;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class OnboardingFileInspectionServiceTest {
    private final ApplicationProperties properties = new ApplicationProperties();
    private final OnboardingFileInspectionService service = new OnboardingFileInspectionService(properties);
    private final OnboardingTemplateService templates = new OnboardingTemplateService();

    @Test
    void parsesQuotedSemicolonCsv() {
        byte[] csv = "riferimento;nome;descrizione\r\nfiati;Fiati;\"Legni; ottoni\"\r\n".getBytes(StandardCharsets.UTF_8);
        var result = service.inspect(csv, OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class));
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).values().get("descrizione")).isEqualTo("Legni; ottoni");
    }

    @Test
    void readsThePlaybackColumnsAndAcceptsFilesPreparedWithoutThem() {
        byte[] current = "riferimento,nome,descrizione,suono,trasposizione,trasposizione_chiave_basso\r\nflic,Flicorno tenore,,57,-14,0\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] previous = "riferimento,nome,descrizione\r\nflic,Flicorno tenore,\r\n".getBytes(StandardCharsets.UTF_8);

        var withColumns = service.inspect(current, OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class));
        var withoutColumns = service.inspect(previous, OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class));

        assertThat(withColumns.rows().get(0).values()).containsEntry("suono", "57").containsEntry("trasposizione", "-14").containsEntry("trasposizione_chiave_basso", "0");
        assertThat(withoutColumns.rows().get(0).values()).containsEntry("suono", "").containsEntry("trasposizione", "");
    }

    @Test
    void rejectsFilesWithOnlySomeOfTheOptionalColumns() {
        byte[] csv = "riferimento,nome,descrizione,suono\r\nflic,Flicorno tenore,,57\r\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.inspect(csv, OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class)))
            .isInstanceOf(OnboardingFileInspectionService.InspectionException.class)
            .hasMessageContaining("intestazioni");
    }

    @Test
    void rejectsFormulaCells() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templates.xlsx())); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.getSheet("Strumenti").createRow(1);
            row.createCell(0).setCellValue("ref");
            row.createCell(1).setCellFormula("1+1");
            workbook.write(output);
            bytes = output.toByteArray();
        }
        assertThatThrownBy(() -> service.inspect(bytes, OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS)))
            .isInstanceOf(OnboardingFileInspectionService.InspectionException.class).hasMessageContaining("formule");
    }

    @Test
    void validatesTheConfiguredFiveThousandRowBatchWithinTheReleaseTarget() {
        StringBuilder csv = new StringBuilder("riferimento,nome,descrizione\r\n");
        for (int row = 1; row <= 5_000; row++)
            csv.append("REF-").append(row).append(",Strumento ").append(row).append(",\r\n");

        var inspection = assertTimeoutPreemptively(
            Duration.ofSeconds(60),
            () -> service.inspect(csv.toString().getBytes(StandardCharsets.UTF_8), OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class))
        );

        assertThat(inspection.rows()).hasSize(5_000);
    }

    /** Macro, collegamenti esterni e oggetti incorporati vivono in parti del pacchetto, non nei fogli. */
    @ParameterizedTest
    @CsvSource({
        "xl/vbaProject.bin,MACRO_NOT_ALLOWED",
        "xl/externalLinks/externalLink1.xml,FILE_EXTERNAL_LINKS",
        "xl/embeddings/oleObject1.bin,FILE_EMBEDDED_OBJECT",
        "xl/activeX/activeX1.xml,FILE_EMBEDDED_OBJECT"
    })
    void refusesAWorkbookCarryingSomethingOtherThanItsSheets(String part, String code) throws Exception {
        byte[] workbook = withExtraPart(templates.xlsx(), part);

        assertThatThrownBy(() -> service.inspect(workbook, OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS)))
            .isInstanceOf(OnboardingFileInspectionService.InspectionException.class)
            .extracting(exception -> ((OnboardingFileInspectionService.InspectionException) exception).getCode())
            .isEqualTo(code);
    }

    /** Il formato viene dal contenuto: l'estensione e il MIME dichiarato dal client non decidono nulla. */
    @Test
    void refusesAFileWhoseContentDoesNotMatchTheDeclaredFormat() throws Exception {
        byte[] workbook = templates.xlsx();
        byte[] oldExcel = new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1, 2, 3, 4 };
        byte[] csvWithNul = ("riferimento,nome,descrizione\r\nSTR-1,Clarinetto," + (char) 0 + "\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(codeOf(() -> service.inspect(workbook, OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class))))
            .isEqualTo("FILE_UNSUPPORTED_FORMAT");
        assertThat(codeOf(() -> service.inspect(csvWithNul, OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class))))
            .isEqualTo("FILE_UNSUPPORTED_FORMAT");
        assertThat(codeOf(() -> service.inspect("riferimento,nome".getBytes(StandardCharsets.UTF_8), OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS))))
            .isEqualTo("FILE_UNSUPPORTED_FORMAT");
        assertThatThrownBy(() -> service.inspect(oldExcel, OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS)))
            .hasMessageContaining(".xls");
    }

    @Test
    void refusesACellLongerThanTheLimitAndAFileWithTooManyCells() throws Exception {
        byte[] workbook = workbookWithRows(3, 40);

        properties.getOnboarding().setMaxCellLength(8);
        assertThat(codeOf(() -> service.inspect(workbook, OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS))))
            .isEqualTo("CELL_TOO_LONG");

        properties.getOnboarding().setMaxCellLength(10_000);
        properties.getOnboarding().setMaxCells(4);
        assertThat(codeOf(() -> service.inspect(workbook, OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS))))
            .isEqualTo("TOO_MANY_CELLS");
    }

    /** Il carico della specifica letto in streaming: il workbook non diventa mai un modello a oggetti. */
    @Test
    void readsAFiveThousandRowWorkbookWithinTheReleaseTarget() throws Exception {
        byte[] workbook = workbookWithRows(5_000, 12);

        var inspection = assertTimeoutPreemptively(
            Duration.ofSeconds(60),
            () -> service.inspect(workbook, OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS))
        );

        assertThat(inspection.rows()).hasSize(5_000);
        assertThat(inspection.rows().get(4_999).values().get("riferimento")).isEqualTo("REF-5000");
    }

    private String codeOf(ThrowableAssert.ThrowingCallable call) {
        try {
            call.call();
        } catch (OnboardingFileInspectionService.InspectionException exception) {
            return exception.getCode();
        } catch (Throwable throwable) {
            throw new AssertionError("Attesa una InspectionException", throwable);
        }
        throw new AssertionError("Il file è stato accettato");
    }

    /** Il template con le righe chieste nel foglio degli strumenti, scritte in streaming. */
    private byte[] workbookWithRows(int rows, int nameLength) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templates.xlsx())); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheet("Strumenti");
            for (int index = 1; index <= rows; index++) {
                var row = sheet.createRow(index);
                row.createCell(0).setCellValue("REF-" + index);
                row.createCell(1).setCellValue("S".repeat(Math.max(1, nameLength)) + index);
                row.createCell(2).setCellValue("");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    /**
     * Aggiunge al pacchetto una parte che un template non ha mai, dichiarandola nei tipi di contenuto come fa un
     * file reale: il workbook resta valido, quindi il rifiuto arriva dal controllo delle parti e non dal parser.
     */
    private byte[] withExtraPart(byte[] xlsx, String part) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream source = new ZipInputStream(new ByteArrayInputStream(xlsx)); ZipOutputStream target = new ZipOutputStream(output)) {
            for (ZipEntry entry = source.getNextEntry(); entry != null; entry = source.getNextEntry()) {
                byte[] content = source.readAllBytes();
                if ("[Content_Types].xml".equals(entry.getName())) {
                    String types = new String(content, StandardCharsets.UTF_8)
                        .replace("</Types>", "<Override PartName=\"/" + part + "\" ContentType=\"application/octet-stream\"/></Types>");
                    content = types.getBytes(StandardCharsets.UTF_8);
                }
                target.putNextEntry(new ZipEntry(entry.getName()));
                target.write(content);
                target.closeEntry();
            }
            target.putNextEntry(new ZipEntry(part));
            target.write(new byte[] { 1, 2, 3 });
            target.closeEntry();
        }
        return output.toByteArray();
    }
}
