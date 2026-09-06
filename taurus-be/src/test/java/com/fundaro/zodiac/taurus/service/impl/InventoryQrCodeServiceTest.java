package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryQrRotation;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryQrRotationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class InventoryQrCodeServiceTest {
    private final InventoryItemRepository items = mock(InventoryItemRepository.class);
    private final InventoryQrRotationRepository rotations = mock(InventoryQrRotationRepository.class);
    private final NotificationOutboxPublisher notifications = mock(NotificationOutboxPublisher.class);
    private final ApplicationProperties properties = properties();
    private final InventoryQrCodeService service = new InventoryQrCodeService(items, rotations, properties, notifications);

    @Test
    void issuesAnOpaqueVersionFourIdentifier() {
        InventoryItem item = new InventoryItem();
        service.issueNew(item, "actor-1");
        assertThat(item.getQrPublicId()).isNotNull();
        assertThat(item.getQrPublicId().version()).isEqualTo(4);
        assertThat(item.getQrVersion()).isEqualTo(1);
        assertThat(item.getQrIssuedBy()).isEqualTo("actor-1");
        assertThat(service.publicUrl(item)).isEqualTo("https://taurus.example/inventory/scan/v1/" + item.getQrPublicId());
    }

    @Test
    void rotationStoresOnlyDigestsAndInvalidatesThePreviousIdentifier() {
        InventoryItem item = new InventoryItem();
        item.setId(42L);
        item.setInventoryNumber("LEG-12");
        item.setQrPublicId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        item.setQrVersion(1);
        when(items.findForUpdate(42L)).thenReturn(Optional.of(item));

        service.rotate(42L, "Etichetta smarrita", authentication());

        assertThat(item.getQrVersion()).isEqualTo(2);
        assertThat(item.getQrPublicId()).isNotEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        ArgumentCaptor<InventoryQrRotation> captor = ArgumentCaptor.forClass(InventoryQrRotation.class);
        verify(rotations).save(captor.capture());
        assertThat(captor.getValue().getPreviousCodeDigest()).hasSize(64).doesNotContain("550e8400");
        assertThat(captor.getValue().getNewCodeDigest()).hasSize(64).doesNotContain(item.getQrPublicId().toString());
        verify(notifications).enqueue(any());
    }

    private static ApplicationProperties properties() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getInventory().getQr().setEnabled(true);
        properties.getInventory().getQr().setPublicBaseUrl("https://taurus.example/");
        return properties;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "actor-1", "tenant", "tenant-a"));
        return new JwtAuthenticationToken(jwt);
    }
}
