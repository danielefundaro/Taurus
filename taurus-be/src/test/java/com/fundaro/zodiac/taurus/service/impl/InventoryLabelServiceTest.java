package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelEntry;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelLayout;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelRequest;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class InventoryLabelServiceTest {
    private static final String URL = "https://taurus.example/inventory/scan/v1/550e8400-e29b-41d4-a716-446655440000";
    private final InventoryItemRepository items = mock(InventoryItemRepository.class);
    private final InventoryQrCodeService qrCodes = mock(InventoryQrCodeService.class);
    private final TenantsService tenants = mock(TenantsService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InventoryLabelService service = service(30_000, 2);
    private final InventoryItem item = item();
    private final JwtAuthenticationToken token = authentication();

    @BeforeEach
    void setUp() {
        when(items.findAllByIdInAndDeletedFalse(List.of(42L))).thenReturn(List.of(item));
        when(qrCodes.publicUrl(item)).thenReturn(URL);
        TenantsDTO tenant = new TenantsDTO();
        tenant.setName("Banda di Test");
        when(tenants.findByCode("tenant-a", token)).thenReturn(Optional.of(tenant));
    }

    @AfterEach
    void tearDown() {
        service.stopRenderer();
    }

    @Test
    void createsPhysicalSingleLabelsWithoutSensitiveItemData() throws IOException {
        byte[] pdf = service.labels(new LabelRequest(LabelLayout.SINGLE_62X40, 0, false, List.of(new LabelEntry(42L, 2))), token).bytes();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(62 * 72f / 25.4f, within(0.1f));
            assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(40 * 72f / 25.4f, within(0.1f));
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Banda di Test", "LEG-0012", "Leggio pieghevole", "Rif. 55440000", "Scansiona con Taurus");
            assertThat(text).doesNotContain("9999", "GOOD", "Quantita", "550e8400");
        }
        assertThat(registry.get("taurus.inventory.label.generation").tags("layout", "single_62x40", "outcome", "done").counter().count()).isEqualTo(1);
        assertThat(registry.get("taurus.inventory.label.count").tag("layout", "single_62x40").summary().totalAmount()).isEqualTo(2);
    }

    /** Criterio 11: il QR estratto dal PDF, renderizzato come lo vedrebbe una stampante, si decodifica nell'URL atteso. */
    @Test
    void theQrDrawnInThePdfDecodesToTheExpectedUrl() throws Exception {
        byte[] pdf = service.labels(new LabelRequest(LabelLayout.SINGLE_62X40, 0, false, List.of(new LabelEntry(42L, 1))), token).bytes();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).as("QR vettoriale, senza immagini").isEmpty();
            BufferedImage page = new PDFRenderer(document).renderImageWithDPI(0, 300);
            int[] pixels = page.getRGB(0, 0, page.getWidth(), page.getHeight(), null, 0, page.getWidth());
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(page.getWidth(), page.getHeight(), pixels)));
            assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(URL);
        }
    }

    @Test
    void theA4SheetStartsFromTheRequestedCellAndPaginates() throws IOException {
        byte[] pdf = service.labels(new LabelRequest(LabelLayout.A4_GRID_3X8, 20, true, List.of(new LabelEntry(42L, 30))), token).bytes();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(PDRectangle.A4.getWidth(), within(0.1f));
            assertThat(labelsOnPage(document, 1)).isEqualTo(4);
            assertThat(labelsOnPage(document, 2)).isEqualTo(24);
            assertThat(labelsOnPage(document, 3)).isEqualTo(2);
        }
    }

    @Test
    void tenFullA4PagesAreTheLargestBatch() throws IOException {
        byte[] pdf = service.labels(new LabelRequest(LabelLayout.A4_GRID_3X8, 0, false, List.of(new LabelEntry(42L, 240))), token).bytes();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(10);
        }
    }

    @Test
    void rejectsBatchesBeyondTheLimitsBeforeReadingTheItems() {
        assertRejected(new LabelRequest(LabelLayout.SINGLE_62X40, 0, false, List.of(new LabelEntry(42L, 21))), "inventory.labels.singleLimit");
        assertRejected(new LabelRequest(LabelLayout.SINGLE_62X40, 3, false, List.of(new LabelEntry(42L, 1))), "inventory.labels.singleLimit");
        assertRejected(new LabelRequest(LabelLayout.A4_GRID_3X8, 1, false, List.of(new LabelEntry(42L, 240))), "inventory.labels.sheetLimit");
        assertRejected(new LabelRequest(LabelLayout.A4_GRID_3X8, 0, false, List.of(new LabelEntry(42L, 1), new LabelEntry(42L, 1))), "inventory.labels.duplicate");
        verify(items, never()).findAllByIdInAndDeletedFalse(anyList());
        assertThat(registry.get("taurus.inventory.label.generation").tags("layout", "a4_grid_3x8", "outcome", "rejected").counter().count()).isEqualTo(2);
    }

    @Test
    void aMissingItemProducesNoPartialPdf() {
        when(items.findAllByIdInAndDeletedFalse(List.of(42L, 43L))).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.labels(new LabelRequest(LabelLayout.A4_GRID_3X8, 0, false,
            List.of(new LabelEntry(42L, 1), new LabelEntry(43L, 1))), token))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(tenants, never()).findByCode(any(), any());
    }

    /** Oltre il limite di tempo la richiesta risponde 503 e la metrica registra l'esito. */
    @Test
    void answersUnavailableWhenRenderingExceedsTheTimeLimit() {
        InventoryLabelService slow = service(1, 2);
        try {
            assertThatThrownBy(() -> slow.labels(new LabelRequest(LabelLayout.A4_GRID_3X8, 0, false, List.of(new LabelEntry(42L, 240))), token))
                .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                    assertThat(error.getErrorKey()).isEqualTo("inventory.labels.timeout");
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
            assertThat(registry.get("taurus.inventory.label.generation").tags("layout", "a4_grid_3x8", "outcome", "timeout").counter().count()).isEqualTo(1);
        } finally {
            slow.stopRenderer();
        }
    }

    /** Senza posti liberi nel pool il 503 arriva subito, senza attendere il limite di tempo. */
    @Test
    void answersBusyWhenEveryRendererIsTaken() throws Exception {
        InventoryLabelService single = service(30_000, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> single.rendered(() -> {
                started.countDown();
                release.await();
                return "done";
            }));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> single.rendered(() -> "second"))
                .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                    assertThat(error.getErrorKey()).isEqualTo("inventory.labels.busy");
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("done");
        } finally {
            release.countDown();
            single.stopRenderer();
        }
    }

    @Test
    void renderingFailuresReachTheCallerUnchanged() {
        assertThatThrownBy(() -> service.rendered(() -> {
            throw new IOException("disk full");
        })).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("disk full");
        assertThatThrownBy(() -> service.rendered(() -> {
            throw new IllegalArgumentException("bad label");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("bad label");
    }

    private void assertRejected(LabelRequest request, String key) {
        assertThatThrownBy(() -> service.labels(request, token))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo(key);
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    private static int labelsOnPage(PDDocument document, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document).split("Scansiona con Taurus", -1).length - 1;
    }

    private InventoryLabelService service(long timeoutMillis, int renderers) {
        return new InventoryLabelService(items, qrCodes, tenants, new InventoryQrMetrics(registry), timeoutMillis, renderers);
    }

    private static InventoryItem item() {
        InventoryItem item = new InventoryItem();
        item.setId(42L);
        item.setInventoryNumber("LEG-0012");
        item.setName("Leggio pieghevole");
        item.setTotalQuantity(9);
        item.setEstimatedUnitValue(java.math.BigDecimal.valueOf(9999));
        item.setConditionStatus(InventoryCondition.GOOD);
        item.setQrPublicId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        return item;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "actor-1", "tenant", "tenant-a"));
        return new JwtAuthenticationToken(jwt);
    }
}
