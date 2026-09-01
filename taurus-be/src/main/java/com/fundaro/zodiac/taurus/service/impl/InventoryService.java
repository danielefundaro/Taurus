package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentDecision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRevision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItemPhoto;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturn;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnPhoto;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryRevisionReason;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentDecisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRevisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnPhotoRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentScope;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAdminSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoOrderRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryUserSummaryDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class InventoryService {

    private static final String ENTITY = "inventory";
    private static final long MAX_PHOTO_SIZE = 10L * 1024 * 1024;
    private static final int MAX_PHOTOS_PER_ITEM = 20;
    static final List<InventoryAssignmentStatus> OUTSTANDING_ASSIGNMENT_STATUSES = List.of(
        InventoryAssignmentStatus.ACTIVE,
        InventoryAssignmentStatus.PARTIALLY_RETURNED
    );

    private final InventoryItemRepository itemRepository;
    private final InventoryItemPhotoRepository photoRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryAssignmentRevisionRepository revisionRepository;
    private final InventoryAssignmentDecisionRepository decisionRepository;
    private final InventoryReturnRepository returnRepository;
    private final InventoryReturnPhotoRepository returnPhotoRepository;
    private final UsersService usersService;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final ObjectMapper objectMapper;

    public InventoryService(
        InventoryItemRepository itemRepository,
        InventoryItemPhotoRepository photoRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryAssignmentRevisionRepository revisionRepository,
        InventoryAssignmentDecisionRepository decisionRepository,
        InventoryReturnRepository returnRepository,
        InventoryReturnPhotoRepository returnPhotoRepository,
        UsersService usersService,
        MediaService mediaService,
        MediaRepository mediaRepository,
        ObjectMapper objectMapper
    ) {
        this.itemRepository = itemRepository;
        this.photoRepository = photoRepository;
        this.assignmentRepository = assignmentRepository;
        this.revisionRepository = revisionRepository;
        this.decisionRepository = decisionRepository;
        this.returnRepository = returnRepository;
        this.returnPhotoRepository = returnPhotoRepository;
        this.usersService = usersService;
        this.mediaService = mediaService;
        this.mediaRepository = mediaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemDTO> findItems(String query, Pageable pageable, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        Page<InventoryItem> page = query == null || query.isBlank()
            ? itemRepository.findAllByDeletedFalse(pageable)
            : itemRepository.search(query.trim(), pageable);
        return page.map(item -> toItemDto(item, false));
    }

    @Transactional(readOnly = true)
    public InventoryAdminSummaryDTO getAdminSummary(AbstractAuthenticationToken token) {
        tenant(token);
        long registeredItems = itemRepository.countByDeletedFalse();
        long totalQuantity = itemRepository.sumTotalQuantity();
        long assignedQuantity = assignmentRepository.sumOutstanding(OUTSTANDING_ASSIGNMENT_STATUSES);
        long availableQuantity = Math.max(0, totalQuantity - assignedQuantity);
        long pendingDecisions = decisionRepository.countPendingCurrentRevisions(OUTSTANDING_ASSIGNMENT_STATUSES);
        long pendingReturns = returnRepository.countByDeletedFalseAndStatus(InventoryReturnStatus.REQUESTED);
        return new InventoryAdminSummaryDTO(
            registeredItems,
            totalQuantity,
            assignedQuantity,
            availableQuantity,
            pendingDecisions,
            pendingReturns
        );
    }

    @Transactional(readOnly = true)
    public InventoryItemDTO findItem(long id, AbstractAuthenticationToken token) {
        tenant(token);
        return toItemDto(requiredItem(id), true);
    }

    public InventoryItemDTO createItem(InventoryItemRequest request, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        String number = request.inventoryNumber().trim();
        if (itemRepository.existsByInventoryNumberIgnoreCaseAndDeletedFalse(number)) {
            throw error(HttpStatus.CONFLICT, "Numero inventariale già utilizzato", "inventory.number.exists");
        }
        validateValueCurrency(request.estimatedUnitValue(), request.currency());
        ZonedDateTime now = ZonedDateTime.now();
        InventoryItem item = new InventoryItem();
        item.setDeleted(false);
        item.setInsertDate(now);
        item.setInsertBy(actor);
        item.setEditDate(now);
        item.setEditBy(actor);
        apply(item, request);
        itemRepository.save(item);
        return toItemDto(item, true);
    }

    public InventoryItemDTO updateItem(long id, InventoryItemRequest request, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryItem item = itemRepository.findForUpdate(id).orElseThrow(() -> notFound("Oggetto inventario non trovato"));
        if (itemRepository.existsByInventoryNumberIgnoreCaseAndIdNotAndDeletedFalse(request.inventoryNumber().trim(), id)) {
            throw error(HttpStatus.CONFLICT, "Numero inventariale già utilizzato", "inventory.number.exists");
        }
        validateValueCurrency(request.estimatedUnitValue(), request.currency());
        long outstanding = outstanding(item.getId());
        if (request.totalQuantity() < outstanding) {
            throw error(HttpStatus.CONFLICT, "La quantità totale non può essere inferiore a quella ancora assegnata", "inventory.quantity.belowAssigned");
        }
        boolean relevantChange = relevantItemChange(item, request);
        apply(item, request);
        touch(item, actor);
        itemRepository.save(item);
        if (relevantChange) {
            reviseOutstandingAssignments(item, InventoryRevisionReason.ITEM_UPDATED, actor);
        }
        return toItemDto(item, true);
    }

    public void deleteItem(long id, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        InventoryItem item = itemRepository.findForUpdate(id).orElseThrow(() -> notFound("Oggetto inventario non trovato"));
        if (outstanding(id) > 0) {
            throw error(HttpStatus.CONFLICT, "Non è possibile eliminare un oggetto ancora assegnato", "inventory.item.assigned");
        }
        item.setDeleted(true);
        touch(item, actor(token));
        itemRepository.save(item);
    }

    public InventoryAssignmentDTO assign(long itemId, InventoryAssignmentRequest request, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryItem item = itemRepository.findForUpdate(itemId).orElseThrow(() -> notFound("Oggetto inventario non trovato"));
        long available = item.getTotalQuantity() - outstanding(itemId);
        if (request.quantity() > available) {
            throw error(HttpStatus.CONFLICT, "Quantità disponibile insufficiente", "inventory.quantity.unavailable");
        }
        UsersDTO user = usersService.findOne(request.userIndex(), token)
            .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Utente non trovato nel tenant", "inventory.user.notFound"));
        if (user.getKeycloakId() == null || user.getKeycloakId().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, "L'utente non dispone di un'identità autenticabile", "inventory.user.identityMissing");
        }
        ZonedDateTime now = ZonedDateTime.now();
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setDeleted(false);
        assignment.setInsertDate(now);
        assignment.setInsertBy(actor);
        assignment.setEditDate(now);
        assignment.setEditBy(actor);
        assignment.setItem(item);
        assignment.setUserIndex(user.getId());
        assignment.setUserKeycloakId(user.getKeycloakId());
        assignment.setUserName(Objects.requireNonNullElse(user.getName(), ""));
        assignment.setUserLastName(Objects.requireNonNullElse(user.getLastName(), ""));
        assignment.setDisplayOrder(request.order());
        assignment.setAssignedQuantity(request.quantity());
        assignment.setReturnedQuantity(0);
        assignment.setAssignedAt(now);
        assignment.setDescription(trimToNull(request.description()));
        assignment.setExpirationDate(request.expirationDate());
        assignment.setStatus(InventoryAssignmentStatus.ACTIVE);
        assignment.setCurrentRevision(0);
        assignmentRepository.save(assignment);
        createRevision(assignment, InventoryRevisionReason.INITIAL_ASSIGNMENT, actor);
        return toAssignmentDto(assignment);
    }

    public InventoryAssignmentDTO updateAssignment(long assignmentId, InventoryAssignmentRequest request, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId)
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        if (!assignment.getUserIndex().equals(request.userIndex())) {
            throw error(HttpStatus.BAD_REQUEST, "Per cambiare utente occorre creare una nuova assegnazione", "inventory.assignment.userImmutable");
        }
        int minimum = assignment.getReturnedQuantity();
        if (request.quantity() < minimum) {
            throw error(HttpStatus.CONFLICT, "La quantità assegnata non può essere inferiore a quella già riconsegnata", "inventory.assignment.quantityInvalid");
        }
        long otherOutstanding = outstanding(assignment.getItem().getId()) - assignment.getOutstandingQuantity();
        if (otherOutstanding + request.quantity() - assignment.getReturnedQuantity() > assignment.getItem().getTotalQuantity()) {
            throw error(HttpStatus.CONFLICT, "Quantità disponibile insufficiente", "inventory.quantity.unavailable");
        }
        boolean relevant = assignment.getAssignedQuantity() != request.quantity()
            || !Objects.equals(assignment.getDescription(), trimToNull(request.description()))
            || !Objects.equals(assignment.getExpirationDate(), request.expirationDate());
        assignment.setAssignedQuantity(request.quantity());
        assignment.setDisplayOrder(request.order());
        assignment.setDescription(trimToNull(request.description()));
        assignment.setExpirationDate(request.expirationDate());
        refreshAssignmentStatus(assignment);
        touch(assignment, actor);
        assignmentRepository.save(assignment);
        if (relevant && assignment.getStatus() != InventoryAssignmentStatus.RETURNED) {
            createRevision(assignment, InventoryRevisionReason.ASSIGNMENT_UPDATED, actor);
        }
        return toAssignmentDto(assignment);
    }

    public void deleteAssignment(long assignmentId, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId)
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        List<InventoryReturn> returns = returnRepository.findAllByAssignment_IdAndDeletedFalseOrderByRequestedAtDesc(assignmentId);
        returns.forEach(value -> softDeleteReturn(value, actor));
        assignment.setDeleted(true);
        touch(assignment, actor);
        assignmentRepository.save(assignment);
    }

    public InventoryAssignmentDTO reissue(long assignmentId, AbstractAuthenticationToken token) {
        tenant(token);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId)
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        InventoryAssignmentRevision current = currentRevision(assignment);
        InventoryAssignmentDecision decision = decisionRepository.findByRevision_Id(current.getId()).orElse(null);
        if (decision == null || decision.getDecision() != InventoryDecisionType.REJECTED) {
            throw error(HttpStatus.CONFLICT, "È possibile riemettere solo una presa visione rifiutata", "inventory.assignment.notRejected");
        }
        createRevision(assignment, InventoryRevisionReason.REISSUED_AFTER_REJECTION, actor(token));
        return toAssignmentDto(assignment);
    }

    @Transactional(readOnly = true)
    public List<InventoryAssignmentDTO> findOwnAssignments(AbstractAuthenticationToken token) {
        tenant(token);
        return assignmentRepository.findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc(actor(token))
            .stream().map(this::toAssignmentDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<InventoryAssignmentSummaryDTO> findOwnAssignments(
        String query,
        InventoryAssignmentScope scope,
        Pageable pageable,
        AbstractAuthenticationToken token
    ) {
        tenant(token);
        List<InventoryAssignmentStatus> statuses = scope == InventoryAssignmentScope.RETURNED
            ? List.of(InventoryAssignmentStatus.RETURNED)
            : OUTSTANDING_ASSIGNMENT_STATUSES;
        String normalizedQuery = trimToNull(query);
        Page<InventoryAssignment> assignments = normalizedQuery == null
            ? assignmentRepository.findAllByUserKeycloakIdAndDeletedFalseAndStatusIn(actor(token), statuses, pageable)
            : assignmentRepository.searchOwn(actor(token), normalizedQuery, statuses, pageable);
        return assignments.map(this::toAssignmentSummaryDto);
    }

    @Transactional(readOnly = true)
    public InventoryUserSummaryDTO getOwnSummary(AbstractAuthenticationToken token) {
        tenant(token);
        String userId = actor(token);
        long possessedItems = assignmentRepository.countByUserKeycloakIdAndDeletedFalseAndStatusIn(
            userId,
            OUTSTANDING_ASSIGNMENT_STATUSES
        );
        long outstandingQuantity = assignmentRepository.sumOutstandingForUser(userId, OUTSTANDING_ASSIGNMENT_STATUSES);
        long pendingDecisions = decisionRepository.countPendingCurrentRevisionsForUser(userId, OUTSTANDING_ASSIGNMENT_STATUSES);
        ZonedDateTime lastAssignedAt = assignmentRepository.findLatestAssignedAtForUser(userId, OUTSTANDING_ASSIGNMENT_STATUSES);
        return new InventoryUserSummaryDTO(possessedItems, outstandingQuantity, pendingDecisions, lastAssignedAt);
    }

    @Transactional(readOnly = true)
    public InventoryAssignmentDTO findOwnAssignment(long assignmentId, AbstractAuthenticationToken token) {
        tenant(token);
        InventoryAssignment assignment = assignmentRepository.findByIdAndUserKeycloakIdAndDeletedFalse(assignmentId, actor(token))
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        return toAssignmentDto(assignment);
    }

    @Transactional(readOnly = true)
    public List<InventoryAssignmentDTO> findUserAssignments(Long userIndex, AbstractAuthenticationToken token) {
        tenant(token);
        return assignmentRepository.findAllByUserIndexAndDeletedFalseOrderByAssignedAtDesc(userIndex)
            .stream().map(this::toAssignmentDto).toList();
    }

    public InventoryAssignmentDTO decide(long assignmentId, InventoryDecisionRequest request, AbstractAuthenticationToken token) {
        String userId = actor(token);
        tenant(token);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId)
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        if (!assignment.getUserKeycloakId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "L'assegnazione non appartiene all'utente autenticato", "inventory.assignment.forbidden");
        }
        InventoryAssignmentRevision revision = currentRevision(assignment);
        if (!MessageDigest.isEqual(revision.getSnapshotHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII), request.revisionHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw error(HttpStatus.CONFLICT, "I dati dell'assegnazione sono cambiati: ricaricare prima di confermare", "inventory.revision.stale");
        }
        if (decisionRepository.findByRevision_Id(revision.getId()).isPresent()) {
            throw error(HttpStatus.CONFLICT, "La scelta per questa revisione è già definitiva", "inventory.decision.immutable");
        }
        String rejectionReason = trimToNull(request.rejectionReason());
        if (request.decision() == InventoryDecisionType.REJECTED && rejectionReason == null) {
            throw error(HttpStatus.BAD_REQUEST, "La motivazione del rifiuto è obbligatoria", "inventory.decision.reasonRequired");
        }
        InventoryAssignmentDecision decision = new InventoryAssignmentDecision();
        decision.initializeAudit(userId);
        decision.setRevision(revision);
        decision.setDecision(request.decision());
        decision.setRejectionReason(request.decision() == InventoryDecisionType.REJECTED ? rejectionReason : null);
        decision.setDecidedAt(ZonedDateTime.now());
        decision.setDecidedBy(userId);
        String authenticatedValue = revision.getSnapshotHash() + "|" + userId + "|" + request.decision().name() + "|" + Objects.requireNonNullElse(rejectionReason, "");
        decision.setAuthenticatedHash(sha256(authenticatedValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        decisionRepository.save(decision);
        return toAssignmentDto(assignment);
    }

    public InventoryReturnDTO requestReturn(long assignmentId, InventoryReturnRequest request, boolean ownerRequired, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId)
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        if (ownerRequired && !assignment.getUserKeycloakId().equals(actor)) {
            throw error(HttpStatus.FORBIDDEN, "L'assegnazione non appartiene all'utente autenticato", "inventory.assignment.forbidden");
        }
        long pending = returnRepository.sumQuantities(assignmentId, List.of(InventoryReturnStatus.REQUESTED));
        if (request.quantity() + pending > assignment.getOutstandingQuantity()) {
            throw error(HttpStatus.CONFLICT, "La quantità richiesta supera il materiale ancora da riconsegnare", "inventory.return.quantityInvalid");
        }
        InventoryReturn inventoryReturn = new InventoryReturn();
        inventoryReturn.initializeAudit(actor);
        inventoryReturn.setAssignment(assignment);
        inventoryReturn.setQuantity(request.quantity());
        inventoryReturn.setStatus(InventoryReturnStatus.REQUESTED);
        inventoryReturn.setRequestedAt(ZonedDateTime.now());
        inventoryReturn.setRequestedBy(actor);
        inventoryReturn.setReturnCondition(request.condition());
        inventoryReturn.setNotes(trimToNull(request.notes()));
        return toReturnDto(returnRepository.save(inventoryReturn));
    }

    public InventoryReturnDTO completeReturn(long returnId, InventoryReturnRequest completion, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryReturn candidate = returnRepository.findByIdAndDeletedFalse(returnId)
            .orElseThrow(() -> notFound("Procedura di riconsegna non trovata"));
        InventoryAssignment assignment = assignmentRepository.findForUpdate(candidate.getAssignment().getId())
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        InventoryReturn inventoryReturn = returnRepository.findForUpdate(returnId)
            .orElseThrow(() -> notFound("Procedura di riconsegna non trovata"));
        if (inventoryReturn.getStatus() != InventoryReturnStatus.REQUESTED) {
            throw error(HttpStatus.CONFLICT, "La procedura di riconsegna è già chiusa", "inventory.return.closed");
        }
        int completedQuantity = completion == null ? inventoryReturn.getQuantity() : completion.quantity();
        if (completedQuantity < 1 || completedQuantity > inventoryReturn.getQuantity() || completedQuantity > assignment.getOutstandingQuantity()) {
            throw error(HttpStatus.CONFLICT, "Quantità riconsegnata non valida", "inventory.return.quantityInvalid");
        }
        inventoryReturn.setQuantity(completedQuantity);
        inventoryReturn.setStatus(InventoryReturnStatus.COMPLETED);
        inventoryReturn.setCompletedAt(ZonedDateTime.now());
        inventoryReturn.setCompletedBy(actor);
        inventoryReturn.touchAudit(actor);
        if (completion != null) {
            inventoryReturn.setReturnCondition(completion.condition());
            inventoryReturn.setNotes(trimToNull(completion.notes()));
        }
        assignment.setReturnedQuantity(assignment.getReturnedQuantity() + completedQuantity);
        refreshAssignmentStatus(assignment);
        touch(assignment, actor);
        assignmentRepository.save(assignment);
        return toReturnDto(returnRepository.save(inventoryReturn));
    }

    public InventoryReturnDTO cancelReturn(long returnId, AbstractAuthenticationToken token) {
        tenant(token);
        InventoryReturn candidate = returnRepository.findByIdAndDeletedFalse(returnId)
            .orElseThrow(() -> notFound("Procedura di riconsegna non trovata"));
        assignmentRepository.findForUpdate(candidate.getAssignment().getId())
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        InventoryReturn inventoryReturn = returnRepository.findForUpdate(returnId)
            .orElseThrow(() -> notFound("Procedura di riconsegna non trovata"));
        if (inventoryReturn.getStatus() != InventoryReturnStatus.REQUESTED) {
            throw error(HttpStatus.CONFLICT, "La procedura di riconsegna è già chiusa", "inventory.return.closed");
        }
        inventoryReturn.setStatus(InventoryReturnStatus.CANCELLED);
        inventoryReturn.touchAudit(actor(token));
        return toReturnDto(returnRepository.save(inventoryReturn));
    }

    public void deleteReturn(long returnId, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryReturn candidate = returnRepository.findByIdAndDeletedFalse(returnId)
            .orElseThrow(() -> notFound("Procedura di riconsegna non trovata"));
        InventoryAssignment assignment = assignmentRepository.findForUpdate(candidate.getAssignment().getId())
            .orElseThrow(() -> notFound("Assegnazione non trovata"));
        InventoryReturn inventoryReturn = returnRepository.findForUpdate(returnId)
            .orElseThrow(() -> notFound("Procedura di riconsegna non trovata"));
        if (inventoryReturn.getStatus() == InventoryReturnStatus.COMPLETED) {
            long restoredOutstanding = outstanding(assignment.getItem().getId()) + inventoryReturn.getQuantity();
            if (restoredOutstanding > assignment.getItem().getTotalQuantity()) {
                throw error(
                    HttpStatus.CONFLICT,
                    "La riconsegna non può essere eliminata perché il materiale è già stato riassegnato",
                    "inventory.return.materialReassigned"
                );
            }
            assignment.setReturnedQuantity(assignment.getReturnedQuantity() - inventoryReturn.getQuantity());
            refreshAssignmentStatus(assignment);
            touch(assignment, actor);
            assignmentRepository.save(assignment);
        }
        softDeleteReturn(inventoryReturn, actor);
    }

    public InventoryPhotoDTO addReturnPhoto(long returnId, MultipartFile file, boolean ownerRequired, AbstractAuthenticationToken token) throws IOException {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryReturn inventoryReturn = returnRepository.findByIdAndDeletedFalse(returnId)
            .orElseThrow(() -> notFound("Procedura di riconsegna non trovata"));
        if (ownerRequired && !inventoryReturn.getAssignment().getUserKeycloakId().equals(actor)) {
            throw error(HttpStatus.FORBIDDEN, "La riconsegna non appartiene all'utente autenticato", "inventory.return.forbidden");
        }
        validatePhoto(file);
        if (returnPhotoRepository.countByInventoryReturn_IdAndDeletedFalse(returnId) >= MAX_PHOTOS_PER_ITEM) {
            throw error(HttpStatus.CONFLICT, "Numero massimo di fotografie raggiunto", "inventory.photo.limit");
        }
        String type = file.getContentType().toLowerCase(Locale.ROOT);
        byte[] normalized = normalizeImage(file.getBytes(), type);
        String extension = type.equals("image/png") ? ".png" : ".jpg";
        MediaDTO media = mediaService.store(normalized, safeFileName(file.getOriginalFilename(), extension), type, "inventory-returns", token);
        InventoryReturnPhoto photo = new InventoryReturnPhoto();
        photo.initializeAudit(actor);
        photo.setInventoryReturn(inventoryReturn);
        photo.setMediaAsset(mediaRepository.getReferenceById(media.getId()));
        returnPhotoRepository.save(photo);
        return toPhotoDto(photo);
    }

    @Transactional(readOnly = true)
    public PhotoContent getReturnPhoto(long photoId, boolean ownerRequired, AbstractAuthenticationToken token) throws IOException {
        tenant(token);
        InventoryReturnPhoto photo = returnPhotoRepository.findByIdAndDeletedFalse(photoId)
            .orElseThrow(() -> notFound("Fotografia di riconsegna non trovata"));
        if (ownerRequired && !photo.getInventoryReturn().getAssignment().getUserKeycloakId().equals(actor(token))) {
            throw error(HttpStatus.FORBIDDEN, "La fotografia non appartiene all'utente autenticato", "inventory.photo.forbidden");
        }
        MediaService.MediaContent content = mediaService.getContent(photo.getMediaAsset().getId(), token);
        return new PhotoContent(content.fileName(), content.mimeType(), content.bytes());
    }

    public InventoryPhotoDTO addPhoto(long itemId, MultipartFile file, AbstractAuthenticationToken token) throws IOException {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryItem item = itemRepository.findForUpdate(itemId).orElseThrow(() -> notFound("Oggetto inventario non trovato"));
        validatePhoto(file);
        String type = file.getContentType().toLowerCase(Locale.ROOT);
        if (photoRepository.countByItem_IdAndDeletedFalse(itemId) >= MAX_PHOTOS_PER_ITEM) {
            throw error(HttpStatus.CONFLICT, "Numero massimo di fotografie raggiunto", "inventory.photo.limit");
        }
        byte[] normalized = normalizeImage(file.getBytes(), type);
        String extension = type.equals("image/png") ? ".png" : ".jpg";
        MediaDTO media = mediaService.store(normalized, safeFileName(file.getOriginalFilename(), extension), type, "inventory", token);
        InventoryItemPhoto photo = new InventoryItemPhoto();
        photo.initializeAudit(actor);
        photo.setItem(item);
        photo.setMediaAsset(mediaRepository.getReferenceById(media.getId()));
        long activePhotoCount = photoRepository.countByItem_IdAndDeletedFalse(itemId);
        photo.setDisplayOrder((int) activePhotoCount);
        photo.setPreview(activePhotoCount == 0);
        photoRepository.save(photo);
        reviseOutstandingAssignments(item, InventoryRevisionReason.PHOTO_UPDATED, actor);
        return toPhotoDto(photo);
    }

    @Transactional(readOnly = true)
    public PhotoContent getPhoto(long photoId, boolean ownerRequired, AbstractAuthenticationToken token) throws IOException {
        tenant(token);
        InventoryItemPhoto photo = photoRepository.findByIdAndDeletedFalse(photoId)
            .orElseThrow(() -> notFound("Fotografia non trovata"));
        if (ownerRequired && !assignmentRepository.existsByItem_IdAndUserKeycloakIdAndDeletedFalse(
            photo.getItem().getId(), actor(token))) {
            throw error(HttpStatus.FORBIDDEN, "La fotografia non appartiene a materiale assegnato all'utente", "inventory.photo.forbidden");
        }
        MediaService.MediaContent content = mediaService.getContent(photo.getMediaAsset().getId(), token);
        return new PhotoContent(content.fileName(), content.mimeType(), content.bytes());
    }

    public void deletePhoto(long photoId, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryItemPhoto photo = photoRepository.findByIdAndDeletedFalse(photoId)
            .orElseThrow(() -> notFound("Fotografia non trovata"));
        InventoryItem item = itemRepository.findForUpdate(photo.getItem().getId())
            .orElseThrow(() -> notFound("Oggetto inventario non trovato"));
        photo.setDeleted(true);
        photo.touchAudit(actor);
        photoRepository.save(photo);
        if (photo.isPreview()) {
            photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(item.getId()).stream().findFirst().ifPresent(next -> {
                next.setPreview(true);
                next.touchAudit(actor);
                photoRepository.save(next);
            });
        }
        reviseOutstandingAssignments(item, InventoryRevisionReason.PHOTO_UPDATED, actor);
    }

    public List<InventoryPhotoDTO> reorderPhotos(
        long itemId,
        InventoryPhotoOrderRequest request,
        AbstractAuthenticationToken token
    ) {
        tenant(token);
        String actor = actor(token);
        InventoryItem item = itemRepository.findForUpdate(itemId).orElseThrow(() -> notFound("Oggetto inventario non trovato"));
        List<InventoryItemPhoto> photos = photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(itemId);
        List<Long> requestedIds = request.photoIds();
        if (requestedIds.size() != photos.size() || new HashSet<>(requestedIds).size() != requestedIds.size()
            || !new HashSet<>(requestedIds).equals(photos.stream().map(InventoryItemPhoto::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw error(HttpStatus.BAD_REQUEST, "L'ordine deve contenere tutte e sole le fotografie attive dell'oggetto", "inventory.photo.order.invalid");
        }
        Map<Long, InventoryItemPhoto> photosById = photos.stream().collect(java.util.stream.Collectors.toMap(InventoryItemPhoto::getId, value -> value));
        List<InventoryItemPhoto> ordered = new ArrayList<>(requestedIds.size());
        for (int index = 0; index < requestedIds.size(); index++) {
            InventoryItemPhoto photo = photosById.get(requestedIds.get(index));
            photo.setDisplayOrder(index);
            photo.touchAudit(actor);
            ordered.add(photo);
        }
        photoRepository.saveAll(ordered);
        reviseOutstandingAssignments(item, InventoryRevisionReason.PHOTO_UPDATED, actor);
        return ordered.stream().map(this::toPhotoDto).toList();
    }

    public List<InventoryPhotoDTO> setPreviewPhoto(
        long itemId,
        long photoId,
        AbstractAuthenticationToken token
    ) {
        tenant(token);
        String actor = actor(token);
        InventoryItem item = itemRepository.findForUpdate(itemId).orElseThrow(() -> notFound("Oggetto inventario non trovato"));
        InventoryItemPhoto selected = photoRepository.findByIdAndItem_IdAndDeletedFalse(photoId, itemId)
            .orElseThrow(() -> notFound("Fotografia non trovata"));
        List<InventoryItemPhoto> photos = photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(itemId);
        boolean changed = false;
        for (InventoryItemPhoto photo : photos) {
            boolean preview = Objects.equals(photo.getId(), selected.getId());
            if (photo.isPreview() != preview) {
                photo.setPreview(preview);
                photo.touchAudit(actor);
                changed = true;
            }
        }
        if (changed) {
            photoRepository.saveAll(photos);
            reviseOutstandingAssignments(item, InventoryRevisionReason.PHOTO_UPDATED, actor);
        }
        return photos.stream().map(this::toPhotoDto).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasOutstandingMaterial(String userKeycloakId) {
        return assignmentRepository.hasOutstanding(userKeycloakId, OUTSTANDING_ASSIGNMENT_STATUSES);
    }

    private InventoryItemDTO toItemDto(InventoryItem item, boolean includeAssignments) {
        int assigned = Math.toIntExact(outstanding(item.getId()));
        List<InventoryPhotoDTO> photos = photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(item.getId())
            .stream().map(this::toPhotoDto).toList();
        List<InventoryAssignmentDTO> assignments = includeAssignments
            ? assignmentRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(item.getId()).stream().map(this::toAssignmentDto).toList()
            : List.of();
        return new InventoryItemDTO(item.getId(), item.getInventoryNumber(), item.getName(), item.getDescription(), item.getTotalQuantity(), assigned,
            item.getTotalQuantity() - assigned, item.getEstimatedUnitValue(), item.getCurrency(), item.getConditionStatus(), item.getConditionNotes(),
            item.getEntityVersion(), photos, assignments);
    }

    private InventoryAssignmentDTO toAssignmentDto(InventoryAssignment assignment) {
        InventoryAssignmentRevision revision = currentRevision(assignment);
        InventoryAssignmentDecision decision = decisionRepository.findByRevision_Id(revision.getId()).orElse(null);
        List<InventoryReturnDTO> returns = returnRepository.findAllByAssignment_IdAndDeletedFalseOrderByRequestedAtDesc(assignment.getId()).stream().map(this::toReturnDto).toList();
        List<InventoryPhotoDTO> photos = photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(assignment.getItem().getId())
            .stream().map(this::toPhotoDto).toList();
        return new InventoryAssignmentDTO(assignment.getId(), assignment.getItem().getId(), assignment.getItem().getInventoryNumber(), assignment.getItem().getName(),
            assignment.getItem().getDescription(), assignment.getItem().getEstimatedUnitValue(), assignment.getItem().getCurrency(), assignment.getItem().getConditionStatus(),
            assignment.getItem().getConditionNotes(), assignment.getExpirationDate(), assignment.getUserIndex(), assignment.getUserName(), assignment.getUserLastName(), assignment.getDisplayOrder(),
            assignment.getAssignedQuantity(), assignment.getReturnedQuantity(), assignment.getOutstandingQuantity(), assignment.getAssignedAt(), assignment.getDescription(),
            assignment.getStatus(), revision.getRevisionNumber(), revision.getSnapshotHash(), revision.getCreatedAt(), decision == null ? null : toDecisionDto(decision), returns, photos);
    }

    private InventoryAssignmentSummaryDTO toAssignmentSummaryDto(InventoryAssignment assignment) {
        InventoryAssignmentRevision revision = currentRevision(assignment);
        InventoryAssignmentDecision decision = decisionRepository.findByRevision_Id(revision.getId()).orElse(null);
        List<InventoryItemPhoto> photos = photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(assignment.getItem().getId());
        InventoryPhotoDTO photo = photos.stream().filter(InventoryItemPhoto::isPreview).findFirst()
            .or(() -> photos.stream().findFirst()).map(this::toPhotoDto).orElse(null);
        return new InventoryAssignmentSummaryDTO(
            assignment.getId(),
            assignment.getItem().getId(),
            assignment.getItem().getInventoryNumber(),
            assignment.getItem().getName(),
            assignment.getItem().getDescription(),
            assignment.getItem().getEstimatedUnitValue(),
            assignment.getItem().getCurrency(),
            assignment.getItem().getConditionStatus(),
            assignment.getExpirationDate(),
            assignment.getAssignedQuantity(),
            assignment.getReturnedQuantity(),
            assignment.getOutstandingQuantity(),
            assignment.getAssignedAt(),
            assignment.getStatus(),
            revision.getRevisionNumber(),
            revision.getCreatedAt(),
            decision == null ? null : toDecisionDto(decision),
            photo
        );
    }

    private InventoryDecisionDTO toDecisionDto(InventoryAssignmentDecision decision) {
        return new InventoryDecisionDTO(decision.getDecision(), decision.getRejectionReason(), decision.getDecidedAt());
    }

    private InventoryReturnDTO toReturnDto(InventoryReturn value) {
        List<InventoryPhotoDTO> photos = returnPhotoRepository.findAllByInventoryReturn_IdAndDeletedFalseOrderByIdAsc(value.getId())
            .stream().map(this::toPhotoDto).toList();
        return new InventoryReturnDTO(value.getId(), value.getQuantity(), value.getStatus(), value.getRequestedAt(), value.getCompletedAt(), value.getReturnCondition(), value.getNotes(), photos);
    }

    private void softDeleteReturn(InventoryReturn inventoryReturn, String actor) {
        List<InventoryReturnPhoto> photos = returnPhotoRepository.findAllByInventoryReturn_IdAndDeletedFalseOrderByIdAsc(inventoryReturn.getId());
        photos.forEach(photo -> {
            photo.setDeleted(true);
            photo.touchAudit(actor);
        });
        returnPhotoRepository.saveAll(photos);
        inventoryReturn.setDeleted(true);
        inventoryReturn.touchAudit(actor);
        returnRepository.save(inventoryReturn);
    }

    private InventoryPhotoDTO toPhotoDto(InventoryItemPhoto photo) {
        var media = photo.getMediaAsset();
        return new InventoryPhotoDTO(photo.getId(), media.getOriginalFilename(), media.getMimeType(), media.getFileSize(), photo.getDisplayOrder(), photo.isPreview());
    }

    private InventoryPhotoDTO toPhotoDto(InventoryReturnPhoto photo) {
        var media = photo.getMediaAsset();
        return new InventoryPhotoDTO(photo.getId(), media.getOriginalFilename(), media.getMimeType(), media.getFileSize(), 0, false);
    }

    private InventoryAssignmentRevision currentRevision(InventoryAssignment assignment) {
        return revisionRepository.findByAssignment_IdAndRevisionNumber(assignment.getId(), assignment.getCurrentRevision())
            .orElseThrow(() -> error(HttpStatus.INTERNAL_SERVER_ERROR, "Revisione inventario non disponibile", "inventory.revision.missing"));
    }

    private void createRevision(InventoryAssignment assignment, InventoryRevisionReason reason, String actor) {
        int number = assignment.getCurrentRevision() + 1;
        String snapshot = snapshot(assignment);
        InventoryAssignmentRevision revision = new InventoryAssignmentRevision();
        revision.initializeAudit(actor);
        revision.setAssignment(assignment);
        revision.setRevisionNumber(number);
        revision.setReason(reason);
        revision.setSnapshotJson(snapshot);
        revision.setSnapshotHash(sha256(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        revision.setCreatedAt(ZonedDateTime.now());
        revision.setCreatedBy(actor);
        revisionRepository.save(revision);
        assignment.setCurrentRevision(number);
        touch(assignment, actor);
        assignmentRepository.save(assignment);
    }

    private String snapshot(InventoryAssignment assignment) {
        InventoryItem item = assignment.getItem();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("inventoryNumber", item.getInventoryNumber());
        root.put("name", item.getName());
        root.put("description", item.getDescription());
        root.put("estimatedUnitValue", item.getEstimatedUnitValue() == null ? null : item.getEstimatedUnitValue().stripTrailingZeros().toPlainString());
        root.put("currency", item.getCurrency());
        root.put("conditionStatus", item.getConditionStatus().name());
        root.put("conditionNotes", item.getConditionNotes());
        root.put("expirationDate", assignment.getExpirationDate() == null ? null : assignment.getExpirationDate().toString());
        root.put("assignedQuantity", assignment.getAssignedQuantity());
        root.put("assignmentDescription", assignment.getDescription());
        List<Map<String, Object>> photos = photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(item.getId())
            .stream().map(photo -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", photo.getId());
                value.put("digest", photo.getMediaAsset().getSha256());
                value.put("displayOrder", photo.getDisplayOrder());
                value.put("preview", photo.isPreview());
                return value;
            }).toList();
        root.put("photos", photos);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile creare la revisione inventario", "inventory.revision.serialization");
        }
    }

    private void reviseOutstandingAssignments(InventoryItem item, InventoryRevisionReason reason, String actor) {
        assignmentRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(item.getId()).stream()
            .filter(assignment -> OUTSTANDING_ASSIGNMENT_STATUSES.contains(assignment.getStatus()))
            .forEach(assignment -> createRevision(assignment, reason, actor));
    }

    private long outstanding(long itemId) {
        return assignmentRepository.sumOutstanding(itemId, OUTSTANDING_ASSIGNMENT_STATUSES);
    }

    private InventoryItem requiredItem(long id) {
        return itemRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> notFound("Oggetto inventario non trovato"));
    }

    private static void apply(InventoryItem item, InventoryItemRequest request) {
        item.setInventoryNumber(request.inventoryNumber().trim());
        item.setName(request.name().trim());
        item.setDescription(trimToNull(request.description()));
        item.setTotalQuantity(request.totalQuantity());
        item.setEstimatedUnitValue(request.estimatedUnitValue());
        item.setCurrency(request.currency() == null ? null : request.currency().toUpperCase(Locale.ROOT));
        item.setConditionStatus(request.conditionStatus());
        item.setConditionNotes(trimToNull(request.conditionNotes()));
    }

    private static boolean relevantItemChange(InventoryItem item, InventoryItemRequest request) {
        return !Objects.equals(item.getInventoryNumber(), request.inventoryNumber().trim())
            || !Objects.equals(item.getName(), request.name().trim())
            || !Objects.equals(item.getDescription(), trimToNull(request.description()))
            || !sameAmount(item.getEstimatedUnitValue(), request.estimatedUnitValue())
            || !Objects.equals(item.getCurrency(), request.currency() == null ? null : request.currency().toUpperCase(Locale.ROOT))
            || item.getConditionStatus() != request.conditionStatus()
            || !Objects.equals(item.getConditionNotes(), trimToNull(request.conditionNotes()));
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static void validateValueCurrency(BigDecimal value, String currency) {
        if ((value == null) != (currency == null || currency.isBlank())) {
            throw error(HttpStatus.BAD_REQUEST, "Valore unitario e valuta devono essere indicati insieme", "inventory.value.currencyRequired");
        }
    }

    private static void refreshAssignmentStatus(InventoryAssignment assignment) {
        if (assignment.getReturnedQuantity() >= assignment.getAssignedQuantity()) {
            assignment.setStatus(InventoryAssignmentStatus.RETURNED);
        } else if (assignment.getReturnedQuantity() > 0) {
            assignment.setStatus(InventoryAssignmentStatus.PARTIALLY_RETURNED);
        } else {
            assignment.setStatus(InventoryAssignmentStatus.ACTIVE);
        }
    }

    private static void touch(com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity entity, String actor) {
        entity.setEditDate(ZonedDateTime.now());
        entity.setEditBy(actor);
    }

    private static byte[] normalizeImage(byte[] bytes, String contentType) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || image.getWidth() > 6000 || image.getHeight() > 6000) {
            throw error(HttpStatus.BAD_REQUEST, "Immagine non valida o dimensioni superiori a 6000x6000", "inventory.photo.invalid");
        }
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
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(output, format, out)) {
                throw error(HttpStatus.BAD_REQUEST, "Impossibile normalizzare l'immagine", "inventory.photo.invalid");
            }
            if (out.size() > MAX_PHOTO_SIZE) {
                throw error(HttpStatus.PAYLOAD_TOO_LARGE, "La fotografia normalizzata supera 10 MB", "inventory.photo.tooLarge");
            }
            return out.toByteArray();
        }
    }

    private static void validatePhoto(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_SIZE) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Ogni fotografia deve avere dimensione massima di 10 MB", "inventory.photo.tooLarge");
        }
        String type = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!type.equals("image/jpeg") && !type.equals("image/png")) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Sono supportate solo immagini JPEG e PNG; WebP non è supportato", "inventory.photo.unsupported");
        }
    }

    private static String safeFileName(String original, String extension) {
        String name = original == null ? "fotografia" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 180) name = name.substring(0, 180);
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? name : name + extension;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (value == null || value.isBlank()) throw error(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", "inventory.identity.missing");
        return value;
    }

    private static String tenant(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getTenantIdFromAuthentication(token);
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Tenant non disponibile", "inventory.tenant.missing");
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static RequestAlertException notFound(String message) {
        return error(HttpStatus.NOT_FOUND, message, "inventory.notFound");
    }

    private static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, ENTITY, key);
    }

    public record PhotoContent(String fileName, String contentType, byte[] bytes) {}
}
