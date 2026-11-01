package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.FakeOmrServer;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrClient;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.TextLine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfProcessingServiceTest {

    private static final String TEXT = "/v1/text-recognitions";

    @TempDir
    Path folder;

    private final FakeOmrServer omr = start();
    private final ApplicationProperties properties = new ApplicationProperties();
    private final MediaService mediaService = mock(MediaService.class);
    private final InstrumentsService instrumentsService = mock(InstrumentsService.class);
    private final TracksDTO track = new TracksDTO();

    @BeforeEach
    void setUp() throws Exception {
        properties.getOmr().setUrl(omr.url());
        properties.getOmr().setApiKey("service-key-0123456789");
        AtomicLong ids = new AtomicLong();
        when(mediaService.store(any(), anyString(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            MediaDTO media = new MediaDTO();
            media.setId(ids.incrementAndGet());
            media.setName(invocation.getArgument(1));
            return media;
        });
        when(instrumentsService.findEntitiesByCriteria(any(), any(), any())).thenReturn(
            new PageImpl<>(List.of(instrument(1L, "Clarinetto"), instrument(2L, "Tromba"), instrument(3L, "Flauto")))
        );
        track.setName("brano");
    }

    @AfterEach
    void stopOmr() {
        omr.close();
    }

    @Test
    void suggestsTheInstrumentsReadInTheLeftMarginAndInTheTopBand() throws IOException {
        omr.answer(TEXT, 200, "application/json", pages(
            List.of(line("Clarinetto 1° in Sib", 0.95, 0.05, 0.5, 0.2, 0.02)),
            List.of(line("Tromba in Sib", 0.97, 0.45, 0.05, 0.2, 0.02))
        ));

        List<SheetsMusicDTO> sheets = service().buildSheets(pdf(2, "Clarinetto", "Tromba"), images(2), track, null);

        assertThat(sheets).extracting(this::instruments).containsExactly(List.of("Clarinetto"), List.of("Tromba"));
        assertThat(sheets).allSatisfy(sheet -> assertThat(sheet.getNeedsReview()).isTrue());
        assertThat(omr.received()).singleElement().satisfies(received -> {
            String body = new String(received.body(), StandardCharsets.ISO_8859_1);
            assertThat(body).contains("filename=\"page-1.png\"", "filename=\"page-2.png\"").doesNotContain("brano");
        });
    }

    @Test
    void ignoresTheLinesOutsideTheZoneOrUnderTheMinimumConfidence() throws IOException {
        omr.answer(TEXT, 200, "application/json", pages(
            List.of(line("Flauto", 0.99, 0.4, 0.5, 0.1, 0.02), line("Tromba", 0.3, 0.05, 0.1, 0.1, 0.02))
        ));

        List<SheetsMusicDTO> sheets = service().buildSheets(pdf(1), images(1), track, null);

        assertThat(sheets).singleElement().satisfies(sheet -> {
            assertThat(sheet.getInstruments()).isNullOrEmpty();
            assertThat(sheet.getNeedsReview()).isNotEqualTo(Boolean.TRUE);
        });
    }

    @Test
    void doesNotReadThePagesWhenThePdfHasUsefulText() throws IOException {
        List<SheetsMusicDTO> sheets = service().buildSheets(pdfWithMarginText("Flauto"), images(1), track, null);

        assertThat(sheets).extracting(this::instruments).containsExactly(List.of("Flauto"));
        assertThat(omr.received()).isEmpty();
    }

    @Test
    void readsTheFirstPageNotExcluded() throws IOException {
        omr.answer(TEXT, 200, "application/json", pages(List.of(line("Clarinetto", 0.9, 0.05, 0.1, 0.1, 0.02))));
        List<String> images = images(3);
        images.set(0, null);
        byte[] second = Files.readAllBytes(Path.of(images.get(1)));

        List<SheetsMusicDTO> sheets = service().buildSheets(pdf(3), images, track, null);

        assertThat(sheets).extracting(this::instruments).containsExactly(List.of("Clarinetto"));
        assertThat(omr.received()).singleElement().satisfies(received -> assertThat(contains(received.body(), second)).isTrue());
    }

    @Test
    void splitsTheScannedPagesInRequestsWithinTheLimits() throws IOException {
        properties.getPdfProcessing().getOcr().setPagesPerRequest(2);
        List<Map<String, Object>> clarinet = List.of(line("Clarinetto", 0.9, 0.05, 0.1, 0.1, 0.02));
        omr.answer(TEXT, 200, "application/json", pages(clarinet, clarinet));

        List<SheetsMusicDTO> sheets = service().buildSheets(pdf(5, "1", "2", "3", "4", "5"), images(5), track, null);

        assertThat(omr.received()).hasSize(3);
        assertThat(sheets).hasSize(5).allSatisfy(sheet -> assertThat(instruments(sheet)).containsExactly("Clarinetto"));
    }

    @Test
    void goesOnWithTheNextRequestsWhenTheServiceRefusesAnImage() throws IOException {
        properties.getPdfProcessing().getOcr().setPagesPerRequest(1);
        omr.answer(TEXT, 422, "application/json", "{\"code\":\"unreadableImage\"}");

        List<SheetsMusicDTO> sheets = service().buildSheets(pdf(2, "1", "2"), images(2), track, null);

        assertThat(omr.received()).hasSize(2);
        assertThat(sheets).hasSize(2);
    }

    @Test
    void readsNothingWhenTheReadingIsDisabled() throws IOException {
        properties.getPdfProcessing().getOcr().setEnabled(false);

        assertNoSuggestions(service().buildSheets(pdf(1), images(1), track, null));
        assertThat(omr.received()).isEmpty();
    }

    @Test
    void readsNothingWithoutTheService() throws IOException {
        properties.getOmr().setUrl("");

        assertNoSuggestions(service().buildSheets(pdf(1), images(1), track, null));
        assertThat(omr.received()).isEmpty();
    }

    @Test
    void completesWithoutSuggestionsWhenTheServiceIsNotReachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        properties.getOmr().setUrl("http://127.0.0.1:" + closedPort);

        assertNoSuggestions(service().buildSheets(pdf(1), images(1), track, null));
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 404, 500, 503 })
    void stopsReadingWhenTheServiceIsNotAvailable(int status) throws IOException {
        properties.getPdfProcessing().getOcr().setPagesPerRequest(1);
        if (status != 404) omr.answer(TEXT, status, "application/json", "{\"code\":\"busy\"}");

        assertNoSuggestions(service().buildSheets(pdf(2, "1", "2"), images(2), track, null));
        assertThat(omr.received()).hasSize(1);
    }

    @Test
    void completesWithoutSuggestionsWhenTheServiceIsTooSlow() throws IOException {
        properties.getPdfProcessing().getOcr().setTimeout(Duration.ofSeconds(1));
        omr.answerAfter(Duration.ofSeconds(3), TEXT, 200, "application/json", pages(List.of(line("Clarinetto", 0.9, 0.05, 0.1, 0.1, 0.02))));
        long started = System.nanoTime();

        assertNoSuggestions(service().buildSheets(pdf(1), images(1), track, null));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void theReadZoneIsTheLeftMarginOrTheTopBand() {
        assertThat(PdfProcessingService.inReadZone(new TextLine("a", 1, 0.0, 0.5, 0.38, 0.02))).isTrue();
        assertThat(PdfProcessingService.inReadZone(new TextLine("a", 1, 0.0, 0.5, 0.42, 0.02))).isFalse();
        assertThat(PdfProcessingService.inReadZone(new TextLine("a", 1, 0.8, 0.13, 0.1, 0.02))).isTrue();
        assertThat(PdfProcessingService.inReadZone(new TextLine("a", 1, 0.8, 0.15, 0.1, 0.02))).isFalse();
    }

    private PdfProcessingService service() {
        return new PdfProcessingService(mediaService, instrumentsService, new OmrClient(properties, new ObjectMapper()), properties);
    }

    private List<String> instruments(SheetsMusicDTO sheet) {
        return sheet.getInstruments() == null ? List.of() : sheet.getInstruments().stream().map(ChildrenEntitiesDTO::getName).sorted().toList();
    }

    private static void assertNoSuggestions(List<SheetsMusicDTO> sheets) {
        assertThat(sheets).isNotEmpty().allSatisfy(sheet -> {
            assertThat(sheet.getInstruments()).isNullOrEmpty();
            assertThat(sheet.getNeedsReview()).isNotEqualTo(Boolean.TRUE);
        });
    }

    /** Page images of different sizes, so that each one can be found in a request. */
    private List<String> images(int count) throws IOException {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Path path = folder.resolve("page-" + i + ".png");
            BufferedImage image = new BufferedImage(20 + i * 7, 30, BufferedImage.TYPE_BYTE_GRAY);
            ImageIO.write(image, "png", path.toFile());
            paths.add(path.toString());
        }
        return paths;
    }

    /** A PDF without text; with titles, one part per title starting on consecutive pages. */
    private static byte[] pdf(int pages, String... titles) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            if (titles.length > 0) {
                PDDocumentOutline outline = new PDDocumentOutline();
                for (int i = 0; i < titles.length; i++) {
                    PDPageFitDestination destination = new PDPageFitDestination();
                    destination.setPage(document.getPage(i));
                    PDOutlineItem item = new PDOutlineItem();
                    item.setTitle(titles[i]);
                    item.setDestination(destination);
                    outline.addLast(item);
                }
                document.getDocumentCatalog().setDocumentOutline(outline);
            }
            return save(document);
        }
    }

    private static byte[] pdfWithMarginText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(20, 700);
                content.showText(text);
                content.endText();
            }
            return save(document);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }

    @SafeVarargs
    private static String pages(List<Map<String, Object>>... pages) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < pages.length; i++) {
            String lines = pages[i].stream().map(PdfProcessingServiceTest::json).collect(Collectors.joining(","));
            entries.add("{\"page\":" + (i + 1) + ",\"lines\":[" + lines + "]}");
        }
        return "{\"engine\":\"RAPIDOCR\",\"version\":\"3.10.0\",\"durationMillis\":1,\"pages\":[" + String.join(",", entries) + "]}";
    }

    private static Map<String, Object> line(String text, double confidence, double x, double y, double width, double height) {
        return Map.of("text", text, "confidence", confidence, "x", x, "y", y, "width", width, "height", height);
    }

    private static String json(Map<String, Object> line) {
        try {
            return new ObjectMapper().writeValueAsString(line);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean contains(byte[] body, byte[] part) {
        outer:
        for (int i = 0; i <= body.length - part.length; i++) {
            for (int j = 0; j < part.length; j++) {
                if (body[i + j] != part[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static InstrumentsDTO instrument(long id, String name) {
        InstrumentsDTO instrument = new InstrumentsDTO();
        instrument.setId(id);
        instrument.setName(name);
        return instrument;
    }

    private static FakeOmrServer start() {
        try {
            return new FakeOmrServer();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
