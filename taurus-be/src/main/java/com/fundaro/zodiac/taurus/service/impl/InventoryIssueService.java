package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssuePhoto;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueReport;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssuePhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssueReportRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryIssueDtos.CreateRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryIssueDtos.Photo;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryIssueDtos.Response;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryIssueDtos.TransitionRequest;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class InventoryIssueService {
    private static final String ENTITY = "inventoryIssue";
    private static final long MAX_PHOTO_SIZE = 10L * 1024 * 1024;
    private static final int MAX_PHOTOS = 20;
    private final InventoryItemRepository itemRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryIssueReportRepository issueRepository;
    private final InventoryIssuePhotoRepository photoRepository;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final NotificationOutboxPublisher notificationPublisher;
    private final InventoryService inventoryService;
    private final InventoryQrCodeService qrCodeService;

    public InventoryIssueService(
        InventoryItemRepository itemRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryIssueReportRepository issueRepository,
        InventoryIssuePhotoRepository photoRepository,
        MediaService mediaService,
        MediaRepository mediaRepository,
        NotificationOutboxPublisher notificationPublisher,
        InventoryService inventoryService,
        InventoryQrCodeService qrCodeService
    ) {
        this.itemRepository = itemRepository;
        this.assignmentRepository = assignmentRepository;
        this.issueRepository = issueRepository;
        this.photoRepository = photoRepository;
        this.mediaService = mediaService;
        this.mediaRepository = mediaRepository;
        this.notificationPublisher = notificationPublisher;
        this.inventoryService = inventoryService;
        this.qrCodeService = qrCodeService;
    }

    public Response createAdmin(long itemId, CreateRequest request, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        InventoryItem item = itemRepository.findForUpdate(itemId).orElseThrow(InventoryIssueService::notFound);
        if (request.reportedQuantity() > item.getTotalQuantity())
            throw badRequest("La quantità segnalata supera la quantità del bene", "inventory.issue.quantity");
        return create(item, null, request, token);
    }

    public Response createOwn(long assignmentId, CreateRequest request, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        String actor = actor(token);
        InventoryAssignment candidate = assignmentRepository.findByIdAndDeletedFalse(assignmentId).orElseThrow(InventoryIssueService::notFound);
        if (!actor.equals(candidate.getUserKeycloakId())) throw notFound();
        InventoryItem item = itemRepository.findForUpdate(candidate.getItem().getId()).orElseThrow(InventoryIssueService::notFound);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId).orElseThrow(InventoryIssueService::notFound);
        if (!actor.equals(assignment.getUserKeycloakId())) throw notFound();
        if (request.reportedQuantity() > assignment.getOutstandingQuantity())
            throw badRequest("La quantità segnalata supera la quantità residua", "inventory.issue.quantity");
        return create(item, assignment, request, token);
    }

    private Response create(InventoryItem item, InventoryAssignment assignment, CreateRequest request, AbstractAuthenticationToken token) {
        String actor = actor(token);
        InventoryIssueReport issue = new InventoryIssueReport();
        issue.initializeAudit(actor);
        issue.setItem(item);
        issue.setAssignment(assignment);
        issue.setReportedQuantity(request.reportedQuantity());
        issue.setSeverity(request.severity());
        issue.setDescription(request.description().trim());
        issue.setStatus(InventoryIssueStatus.OPEN);
        issueRepository.save(issue);
        notifyCreated(issue, token);
        return toDto(issue);
    }

    @Transactional(readOnly = true)
    public List<Response> findForItem(long itemId, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        if (itemRepository.findByIdAndDeletedFalse(itemId).isEmpty()) throw notFound();
        return issueRepository.findAllByItem_IdAndDeletedFalseOrderByInsertDateDesc(itemId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Response findAdmin(long issueId, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        return toDto(issueRepository.findByIdAndDeletedFalse(issueId).orElseThrow(InventoryIssueService::notFound));
    }

    @Transactional(readOnly = true)
    public Response findOwn(long issueId, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        return toDto(issueRepository.findByIdAndAssignment_UserKeycloakIdAndDeletedFalse(issueId, actor(token)).orElseThrow(InventoryIssueService::notFound));
    }

    public Response transition(long issueId, TransitionRequest request, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        String actor = actor(token);
        InventoryIssueReport issue = issueRepository.findForUpdate(issueId).orElseThrow(InventoryIssueService::notFound);
        InventoryItem item = itemRepository.findForUpdate(issue.getItem().getId()).orElseThrow(InventoryIssueService::notFound);
        if (issue.getEntityVersion() != request.version())
            throw conflict("La segnalazione è stata aggiornata da un altro utente", "inventory.issue.version");
        InventoryIssueStatus current = issue.getStatus();
        if (current.isTerminal() || !allowed(current, request.status()))
            throw badRequest("Transizione di stato non consentita", "inventory.issue.transition");
        ZonedDateTime now = ZonedDateTime.now();
        if (request.status() == InventoryIssueStatus.ACKNOWLEDGED) {
            issue.setAcknowledgedAt(now);
            issue.setAcknowledgedBy(actor);
        } else {
            String notes = trimToNull(request.resolutionNotes());
            if (notes == null)
                throw badRequest("Le note di risoluzione sono obbligatorie", "inventory.issue.resolutionNotes");
            issue.setResolutionNotes(notes);
            issue.setResolvedAt(now);
            issue.setResolvedBy(actor);
            if (request.itemConditionStatus() != null && request.itemConditionStatus() != item.getConditionStatus()) {
                inventoryService.updateConditionFromIssue(item, request.itemConditionStatus(), actor);
            }
        }
        issue.setStatus(request.status());
        issue.touchAudit(actor);
        issueRepository.saveAndFlush(issue);
        notifyTransition(issue, current, token);
        return toDto(issue);
    }

    public Photo addPhoto(long issueId, MultipartFile file, boolean ownerRequired, AbstractAuthenticationToken token) throws IOException {
        qrCodeService.requireEnabled();
        tenant(token);
        String actor = actor(token);
        InventoryIssueReport issue = ownerRequired
            ? issueRepository.findByIdAndAssignment_UserKeycloakIdAndDeletedFalse(issueId, actor).orElseThrow(InventoryIssueService::notFound)
            : issueRepository.findByIdAndDeletedFalse(issueId).orElseThrow(InventoryIssueService::notFound);
        if (ownerRequired && issue.getStatus().isTerminal())
            throw conflict("La segnalazione è già chiusa", "inventory.issue.closed");
        validatePhoto(file);
        if (photoRepository.countByIssue_IdAndDeletedFalse(issueId) >= MAX_PHOTOS)
            throw conflict("Numero massimo di fotografie raggiunto", "inventory.issue.photo.limit");
        String type = Objects.requireNonNull(file.getContentType()).toLowerCase(Locale.ROOT);
        byte[] normalized = normalizeImage(file.getBytes(), type);
        String extension = type.equals("image/png") ? ".png" : ".jpg";
        MediaDTO media = mediaService.store(normalized, safeFileName(file.getOriginalFilename(), extension), type, "inventory-issue-photos", token);
        InventoryIssuePhoto photo = new InventoryIssuePhoto();
        photo.initializeAudit(actor);
        photo.setIssue(issue);
        photo.setMediaAsset(mediaRepository.getReferenceById(media.getId()));
        photo.setDisplayOrder((int) photoRepository.countByIssue_IdAndDeletedFalse(issueId));
        photoRepository.save(photo);
        return toPhoto(photo);
    }

    @Transactional(readOnly = true)
    public InventoryService.PhotoContent getPhoto(long photoId, boolean ownerRequired, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        InventoryIssuePhoto photo = photoRepository.findByIdAndDeletedFalse(photoId).orElseThrow(InventoryIssueService::notFound);
        if (ownerRequired) {
            InventoryAssignment assignment = photo.getIssue().getAssignment();
            if (assignment == null || !actor(token).equals(assignment.getUserKeycloakId())) throw notFound();
        }
        MediaService.MediaContent content = mediaService.getContent(photo.getMediaAsset().getId(), token);
        return new InventoryService.PhotoContent(content.fileName(), content.mimeType(), content.bytes());
    }

    public boolean hasOpenUnsafe(long itemId) {
        return issueRepository.existsByItem_IdAndSeverityAndStatusInAndDeletedFalse(itemId, InventoryIssueSeverity.UNSAFE,
            List.of(InventoryIssueStatus.OPEN, InventoryIssueStatus.ACKNOWLEDGED));
    }

    private Response toDto(InventoryIssueReport issue) {
        List<Photo> photos = photoRepository.findAllByIssue_IdAndDeletedFalseOrderByDisplayOrderAsc(issue.getId()).stream().map(this::toPhoto).toList();
        return new Response(issue.getId(), issue.getItem().getId(), issue.getAssignment() == null ? null : issue.getAssignment().getId(),
            issue.getItem().getInventoryNumber(), issue.getItem().getName(), issue.getReportedQuantity(), issue.getSeverity(), issue.getDescription(), issue.getStatus(),
            issue.getResolutionNotes(), issue.getInsertDate(), issue.getAcknowledgedAt(), issue.getResolvedAt(), issue.getEntityVersion(), photos);
    }

    private Photo toPhoto(InventoryIssuePhoto photo) {
        Media media = photo.getMediaAsset();
        return new Photo(photo.getId(), media.getOriginalFilename(), media.getMimeType(), media.getFileSize(), photo.getDisplayOrder());
    }

    private void notifyCreated(InventoryIssueReport issue, AbstractAuthenticationToken token) {
        String actor = actor(token);
        notificationPublisher.enqueue(new NotificationCommand("inventory-issue:" + issue.getId() + ":created", NotificationSource.INVENTORY,
            "INVENTORY_ISSUE", issue.getId().toString(), "CREATED", "Nuova segnalazione inventario",
            issue.getItem().getInventoryNumber() + " · " + issue.getSeverity(), issue.getSeverity() == InventoryIssueSeverity.UNSAFE ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
            NotificationPreferencePolicy.CONFIGURABLE, "/inventory/items/" + issue.getItem().getId(), actor, displayName(token), adminAudiences(), null));
    }

    private void notifyTransition(InventoryIssueReport issue, InventoryIssueStatus previous, AbstractAuthenticationToken token) {
        Set<NotificationAudience> audiences = new LinkedHashSet<>(adminAudiences());
        if (issue.getAssignment() != null)
            audiences.add(NotificationAudience.user(issue.getAssignment().getUserKeycloakId()));
        String actor = actor(token);
        notificationPublisher.enqueue(new NotificationCommand("inventory-issue:" + issue.getId() + ":" + issue.getStatus(), NotificationSource.INVENTORY,
            "INVENTORY_ISSUE", issue.getId().toString(), issue.getStatus().name(), "Segnalazione inventario aggiornata",
            issue.getItem().getInventoryNumber() + " · " + previous + " → " + issue.getStatus(), NotificationSeverity.INFO,
            NotificationPreferencePolicy.CONFIGURABLE, "/inventory/items/" + issue.getItem().getId(), actor, displayName(token), Set.copyOf(audiences), null));
    }

    private static Set<NotificationAudience> adminAudiences() {
        return Set.of(NotificationAudience.role(RoleEnum.ROLE_ADMIN), NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN));
    }

    private static boolean allowed(InventoryIssueStatus from, InventoryIssueStatus to) {
        return from == InventoryIssueStatus.OPEN
            ? to == InventoryIssueStatus.ACKNOWLEDGED || to == InventoryIssueStatus.RESOLVED || to == InventoryIssueStatus.DISMISSED
            : from == InventoryIssueStatus.ACKNOWLEDGED && (to == InventoryIssueStatus.RESOLVED || to == InventoryIssueStatus.DISMISSED);
    }

    private static byte[] normalizeImage(byte[] bytes, String contentType) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || image.getWidth() > 6000 || image.getHeight() > 6000)
            throw badRequest("Immagine non valida o dimensioni superiori a 6000x6000", "inventory.issue.photo.invalid");
        String format = contentType.equals("image/png") ? "png" : "jpg";
        BufferedImage output = image;
        if (format.equals("jpg") && image.getColorModel().hasAlpha()) {
            output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = output.createGraphics();
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
        }
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(output, format, stream))
                throw badRequest("Impossibile normalizzare l'immagine", "inventory.issue.photo.invalid");
            if (stream.size() > MAX_PHOTO_SIZE)
                throw new RequestAlertException(HttpStatus.PAYLOAD_TOO_LARGE, "La fotografia normalizzata supera 10 MB", ENTITY, "inventory.issue.photo.tooLarge");
            return stream.toByteArray();
        }
    }

    private static void validatePhoto(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_SIZE)
            throw new RequestAlertException(HttpStatus.PAYLOAD_TOO_LARGE, "Ogni fotografia deve avere dimensione massima di 10 MB", ENTITY, "inventory.issue.photo.tooLarge");
        String type = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!type.equals("image/jpeg") && !type.equals("image/png"))
            throw new RequestAlertException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Sono supportate solo immagini JPEG e PNG", ENTITY, "inventory.issue.photo.unsupported");
    }

    private static String safeFileName(String original, String extension) {
        String name = original == null ? "fotografia" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 180) name = name.substring(0, 180);
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? name : name + extension;
    }

    private static String displayName(AbstractAuthenticationToken token) {
        String first = Objects.requireNonNullElse(SecurityUtils.getFirstNameFromAuthentication(token), "");
        String last = Objects.requireNonNullElse(SecurityUtils.getLastNameFromAuthentication(token), "");
        String value = (first + " " + last).trim();
        return value.isBlank() ? actor(token) : value;
    }

    private static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (value == null || value.isBlank())
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", ENTITY, "inventory.identity.missing");
        return value;
    }

    private static void tenant(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getTenantIdFromAuthentication(token);
        if (value == null || value.isBlank()) throw badRequest("Tenant non disponibile", "inventory.tenant.missing");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static RequestAlertException notFound() {
        return new RequestAlertException(HttpStatus.NOT_FOUND, "Segnalazione non disponibile", ENTITY, "inventory.issue.notFound");
    }

    private static RequestAlertException badRequest(String message, String key) {
        return new RequestAlertException(HttpStatus.BAD_REQUEST, message, ENTITY, key);
    }

    private static RequestAlertException conflict(String message, String key) {
        return new RequestAlertException(HttpStatus.CONFLICT, message, ENTITY, key);
    }
}
