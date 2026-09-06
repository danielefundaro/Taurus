package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelEntry;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelLayout;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class InventoryLabelServiceTest {
    private final InventoryItemRepository items = mock(InventoryItemRepository.class);
    private final InventoryQrCodeService qrCodes = mock(InventoryQrCodeService.class);
    private final TenantsService tenants = mock(TenantsService.class);
    private final InventoryLabelService service = new InventoryLabelService(items, qrCodes, tenants);

    @Test
    void createsPhysicalSingleLabelsWithoutSensitiveItemData() throws IOException {
        InventoryItem item = item();
        JwtAuthenticationToken token = authentication();
        when(items.findAllByIdInAndDeletedFalse(List.of(42L))).thenReturn(List.of(item));
        when(qrCodes.publicUrl(item)).thenReturn("https://taurus.example/inventory/scan/v1/" + item.getQrPublicId());
        TenantsDTO tenant = new TenantsDTO();
        tenant.setName("Banda di Test");
        when(tenants.findByCode("tenant-a", token)).thenReturn(Optional.of(tenant));

        byte[] pdf = service.labels(new LabelRequest(LabelLayout.SINGLE_62X40, 0, false, List.of(new LabelEntry(42L, 2))), token).bytes();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(62 * 72f / 25.4f, org.assertj.core.data.Offset.offset(0.1f));
            assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(40 * 72f / 25.4f, org.assertj.core.data.Offset.offset(0.1f));
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Banda di Test", "LEG-0012", "Scansiona con Taurus");
            assertThat(text).doesNotContain("9999", "GOOD", "Quantita");
        }
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
