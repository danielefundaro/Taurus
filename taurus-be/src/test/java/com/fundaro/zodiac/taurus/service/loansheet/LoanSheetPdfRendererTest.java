package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Line;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.Tenant;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoanSheetPdfRendererTest {

    static final Tenant TENANT = new Tenant(
        "Associazione Musicale Esempio APS",
        "Via Roma 10",
        "00100",
        "Roma",
        "RM",
        "info@example.org",
        "06 1234567",
        "associazione@pec.example.org",
        "01234567890",
        "91234567890",
        null
    );
    /**
     * A 1x1 PNG image.
     */
    static final String PIXEL = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private final LoanTemplateDefaults defaults = new LoanTemplateDefaults();
    private final LoanSheetPdfRenderer renderer = new LoanSheetPdfRenderer();

    @Test
    void rendersTheDefaultTemplateWithHeaderFooterTypographicCharactersAndWatermark() throws IOException {
        LoanSheetContent content = LoanSheetSamples.preview(TENANT, defaults.durations(), LocalDate.of(2026, 9, 28));

        byte[] pdf = renderer.render(defaults.content(), LoanSheetModelFactory.build(content), true);
        write("loan-sheet-preview.pdf", pdf);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            assertThat(pages).isEqualTo(8);
            for (int page = 1; page <= pages; page++) {
                String text = pageText(document, page);
                assertThat(text).contains("Pagina " + page + " di " + pages);
                assertThat(text).contains("Sigla dell’Affidatario");
                assertThat(text).contains("Foglio di affidamento n. ________");
                assertThat(streamText(document, page)).contains(LoanSheetPdfRenderer.WATERMARK);
            }
            String all = text(document);
            assertThat(all)
                .contains("dell’Affidatario")
                .contains("«Associazione»")
                .contains("Via Roma 10, 00100 Roma (RM)")
                .contains("info@example.org, tel. 06 1234567, PEC associazione@pec.example.org – P. IVA 01234567890 – C.F. 91234567890")
                .contains("non anteriore a 12 mesi e non successiva a 24 mesi")
                .contains("non oltre 36 mesi complessivi")
                .contains("€ 45,00")
                .contains("Giulia Rossi")
                .contains("Delibera n. 12")
                .doesNotContain("{{")
                .doesNotContain("#");
            assertThat(firstSectionOnPage(document, "3. BENI CONSEGNATI")).isEqualTo(2);
            assertThat(firstSectionOnPage(document, "6. CONDIZIONI DI AFFIDAMENTO")).isEqualTo(3);
        }
    }

    @Test
    void rendersAnIssuedSheetWithLongTablesAndNoWatermark() throws IOException {
        LoanSheetContent content = new LoanSheetContent(
            false,
            TENANT,
            "2026/001",
            defaults.durations(),
            InventoryLoanHolderRole.MEMBER,
            "Mario",
            "Rossi",
            LocalDate.of(1990, 5, 1),
            "mario.rossi@example.org",
            lines(18),
            LocalDate.of(2026, 9, 28),
            "Roma",
            null,
            null,
            null,
            List.of(),
            null
        );

        byte[] pdf = renderer.render(defaults.content(), LoanSheetModelFactory.build(content), false);
        write("loan-sheet-issued.pdf", pdf);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String all = text(document);
            assertThat(all)
                .contains("Foglio di affidamento n. 2026/001")
                .contains("Mario Rossi")
                .contains("01/05/1990")
                .contains("Roma, 28/09/2026")
                .contains("Divisa 18")
                .contains("indicata per ciascuno strumento nella sezione 3")
                .contains("Scadenza: 29/09/2027")
                .contains("delibera / provvedimento n. ________ del ______________")
                .doesNotContain(LoanSheetPdfRenderer.WATERMARK);
        }
    }

    @Test
    void escapesValuesAndExposesOnlyTheData() {
        Map<String, Object> model = Map.of("socio", Map.of("nome", "<b>Mario</b> & co"), "testo", "valore");

        String html = renderer.html("{{socio.nome}}|{{testo.class}}|{{testo.length}}|{{socio.class.name}}", model);

        assertThat(html).isEqualTo("&lt;b&gt;Mario&lt;/b&gt; &amp; co|||");
    }

    @Test
    void loadsEmbeddedImagesButNoExternalResource() throws IOException {
        String template = "<html><body><p>Logo</p><img src=\"" + PIXEL + "\"/>"
            + "<img src=\"http://127.0.0.1:9/logo.png\"/><img src=\"file:///etc/passwd\"/>"
            + "<div style=\"background-image: url(http://127.0.0.1:9/bg.png)\">Fine</div></body></html>";

        byte[] pdf = renderer.render(template, Map.of(), false);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int images = 0;
            for (var name : document.getPage(0).getResources().getXObjectNames()) {
                if (document.getPage(0).getResources().getXObject(name) instanceof PDImageXObject) images++;
            }
            assertThat(images).isEqualTo(1);
            assertThat(text(document)).contains("Logo").contains("Fine");
        }
    }

    static List<Line> lines(int count) {
        List<Line> lines = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            boolean instrument = index % 3 == 1;
            lines.add(new Line(
                index,
                (long) index,
                instrument ? "Strumento " + index : "Divisa " + index,
                instrument ? "Marca modello " + index : null,
                instrument ? "SN-" + index : null,
                "INV-" + index,
                instrument ? "Custodia" : "Taglia 48",
                1,
                new BigDecimal("100.00"),
                InventoryCondition.GOOD,
                null,
                instrument ? InventoryLoanType.FIXED_TERM : InventoryLoanType.PRECARIOUS,
                instrument ? LocalDate.of(2027, 9, 28).plusDays(index) : null
            ));
        }
        return lines;
    }

    private static int firstSectionOnPage(PDDocument document, String heading) throws IOException {
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            if (pageText(document, page).contains(heading)) return page;
        }
        return -1;
    }

    static String text(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document).replaceAll("\\s+", " ");
    }

    private static String pageText(PDDocument document, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document).replaceAll("\\s+", " ");
    }

    /**
     * Text in content-stream order, where the rotated watermark is not interleaved with the page text.
     */
    private static String streamText(PDDocument document, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document).replaceAll("\\s+", "");
    }

    private static void write(String name, byte[] pdf) throws IOException {
        Path folder = Path.of("target", "loan-sheet");
        Files.createDirectories(folder);
        Files.write(folder.resolve(name), pdf);
    }
}
