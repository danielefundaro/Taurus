package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryQrRotation;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryQrRotationRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.RotateResponse;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryQrCodeService {
    private static final String ENTITY = "inventoryQrCode";
    private final InventoryItemRepository itemRepository;
    private final InventoryQrRotationRepository rotationRepository;
    private final ApplicationProperties.QrProperties properties;
    private final NotificationOutboxPublisher notificationPublisher;

    public InventoryQrCodeService(
        InventoryItemRepository itemRepository,
        InventoryQrRotationRepository rotationRepository,
        ApplicationProperties applicationProperties,
        NotificationOutboxPublisher notificationPublisher
    ) {
        this.itemRepository = itemRepository;
        this.rotationRepository = rotationRepository;
        this.properties = applicationProperties.getInventory().getQr();
        this.notificationPublisher = notificationPublisher;
    }

    public void issueNew(InventoryItem item, String actor) {
        ZonedDateTime now = ZonedDateTime.now();
        item.setQrPublicId(UUID.randomUUID());
        item.setQrVersion(1);
        item.setQrIssuedAt(now);
        item.setQrIssuedBy(actor);
    }

    public RotateResponse rotate(long itemId, String reason, AbstractAuthenticationToken token) {
        requireEnabled();
        String actor = actor(token);
        InventoryItem item = itemRepository.findForUpdate(itemId).orElseThrow(InventoryQrCodeService::notFound);
        UUID previous = item.getQrPublicId();
        int previousVersion = item.getQrVersion();
        UUID next = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now();

        item.setQrPublicId(next);
        item.setQrVersion(previousVersion + 1);
        item.setQrIssuedAt(now);
        item.setQrIssuedBy(actor);
        item.touchAudit(actor);
        itemRepository.save(item);

        InventoryQrRotation rotation = new InventoryQrRotation();
        rotation.initializeAudit(actor);
        rotation.setItem(item);
        rotation.setPreviousVersion(previousVersion);
        rotation.setNewVersion(item.getQrVersion());
        rotation.setPreviousCodeDigest(digest(previous));
        rotation.setNewCodeDigest(digest(next));
        rotation.setReason(reason.trim());
        rotation.setRotatedAt(now);
        rotation.setRotatedBy(actor);
        rotationRepository.save(rotation);
        notificationPublisher.enqueue(new NotificationCommand(
            "inventory-qr:" + item.getId() + ":version:" + item.getQrVersion(),
            NotificationSource.INVENTORY,
            "INVENTORY_ITEM",
            item.getId().toString(),
            "QR_ROTATED",
            "Codice QR inventario ruotato",
            item.getInventoryNumber() + " · versione " + item.getQrVersion(),
            NotificationSeverity.WARNING,
            NotificationPreferencePolicy.REQUIRED,
            "/inventory/items/" + item.getId(),
            actor,
            actor,
            Set.of(NotificationAudience.role(RoleEnum.ROLE_ADMIN), NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN)),
            null
        ));
        return new RotateResponse(item.getQrVersion(), now);
    }

    public String publicUrl(InventoryItem item) {
        requireEnabled();
        String base = properties.getPublicBaseUrl().trim().replaceAll("/+$", "");
        return base + "/inventory/scan/v1/" + item.getQrPublicId();
    }

    public void requireEnabled() {
        if (!properties.isEnabled()) throw new RequestAlertException(HttpStatus.NOT_FOUND, "Funzionalità QR inventario non disponibile", ENTITY, "inventory.qr.disabled");
    }

    private static String digest(UUID value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (value == null || value.isBlank()) throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", ENTITY, "inventory.identity.missing");
        return value;
    }

    private static RequestAlertException notFound() {
        return new RequestAlertException(HttpStatus.NOT_FOUND, "Codice inventario non disponibile", ENTITY, "inventory.qr.notFound");
    }
}
