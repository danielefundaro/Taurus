package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.LegalDocument;
import com.fundaro.zodiac.taurus.domain.UserLegalAcceptance;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentState;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import com.fundaro.zodiac.taurus.repository.LegalDocumentRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.LegalService;
import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentStatusDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalStatusDTO;
import com.fundaro.zodiac.taurus.service.dto.PublicLegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.legal.LegalContent;
import com.fundaro.zodiac.taurus.service.legal.LegalMarkdownRenderer;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LegalServiceImpl implements LegalService {

    private static final Logger LOG = LoggerFactory.getLogger(LegalServiceImpl.class);

    private static final String ENTITY_NAME = "LegalDocument";

    private final LegalDocumentRepository documentRepository;
    private final UserLegalAcceptanceRepository acceptanceRepository;
    private final LegalMarkdownRenderer renderer;

    public LegalServiceImpl(
        LegalDocumentRepository documentRepository,
        UserLegalAcceptanceRepository acceptanceRepository,
        LegalMarkdownRenderer renderer
    ) {
        this.documentRepository = documentRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.renderer = renderer;
    }

    @Override
    @Transactional(readOnly = true)
    public LegalStatusDTO getStatus(AbstractAuthenticationToken authenticationToken) {
        String userId = requiredTokenValue(SecurityUtils.getUserIdFromAuthentication(authenticationToken), "user");
        return buildStatus(userId);
    }

    @Override
    public LegalStatusDTO accept(LegalAcceptanceRequestDTO request, AbstractAuthenticationToken authenticationToken) {
        String userId = requiredTokenValue(SecurityUtils.getUserIdFromAuthentication(authenticationToken), "user");
        requiredTokenValue(SecurityUtils.getTenantIdFromAuthentication(authenticationToken), "tenant");
        List<LegalDocument> activeDocuments = documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc();
        Set<Long> activeIds = activeDocuments.stream().map(LegalDocument::getId).collect(Collectors.toSet());

        if (!activeIds.containsAll(request.documentIds())) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "One or more legal documents are no longer active", ENTITY_NAME, "legal.document.stale");
        }

        Map<Long, UserLegalAcceptance> existing = findAcceptances(userId, activeIds);
        Set<Long> missingRequiredIds = activeDocuments
            .stream()
            .filter(document -> Boolean.TRUE.equals(document.getRequired()))
            .map(LegalDocument::getId)
            .filter(id -> !existing.containsKey(id))
            .collect(Collectors.toSet());

        if (!request.documentIds().containsAll(missingRequiredIds)) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "All required legal documents must be confirmed", ENTITY_NAME, "legal.acceptance.incomplete");
        }

        ZonedDateTime now = ZonedDateTime.now();
        List<UserLegalAcceptance> acceptances = new ArrayList<>();
        activeDocuments
            .stream()
            .filter(document -> request.documentIds().contains(document.getId()))
            .filter(document -> !existing.containsKey(document.getId()))
            .forEach(document -> {
                UserLegalAcceptance acceptance = new UserLegalAcceptance();
                acceptance.setLegalDocument(document);
                acceptance.setAcceptedAt(now);
                acceptance.setContentSha256(document.getContentSha256());
                acceptance.setDeleted(false);
                acceptance.setInsertBy(userId);
                acceptance.setInsertDate(now);
                acceptance.setEditBy(userId);
                acceptance.setEditDate(now);
                acceptance.setUserId(userId);
                acceptances.add(acceptance);
            });
        acceptanceRepository.saveAll(acceptances);

        return buildStatus(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegalDocumentDTO> findAllDocuments() {
        return documentRepository.findAllByOrderByDocumentTypeAscPublishedAtDesc().stream().map(document -> toDto(document, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegalDocumentDTO findDocument(Long id) {
        return toDto(find(id), true);
    }

    @Override
    public LegalDocumentDTO createDocument(LegalDocumentDTO dto) {
        if (dto.getId() != null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "A new legal document cannot already have an id", ENTITY_NAME, "id.exists");
        }
        String version = validVersion(dto.getVersion());
        if (documentRepository.existsByDocumentTypeAndVersion(dto.getDocumentType(), version)) {
            throw versionExists();
        }
        LegalDocument document = new LegalDocument();
        copyDraftFields(dto, version, document);
        document.setActive(false);
        return toDto(documentRepository.save(document), true);
    }

    @Override
    public LegalDocumentDTO updateDocument(Long id, LegalDocumentDTO dto) {
        LegalDocument document = find(id);
        if (document.isDraft()) {
            String version = validVersion(dto.getVersion());
            if (documentRepository.existsByDocumentTypeAndVersionAndIdNot(dto.getDocumentType(), version, id)) {
                throw versionExists();
            }
            copyDraftFields(dto, version, document);
            return toDto(documentRepository.save(document), true);
        }
        if (document.isWithoutContent() && sameDetails(document, dto)) {
            String content = validContent(dto.getContent());
            document.setContent(content);
            document.setContentSha256(LegalContent.sha256(content));
            LegalDocument saved = documentRepository.save(document);
            log("completed with its text", saved);
            return toDto(saved, true);
        }
        throw publishedImmutable();
    }

    @Override
    public void deleteDocument(Long id) {
        LegalDocument document = find(id);
        if (!document.isDraft()) {
            throw publishedImmutable();
        }
        documentRepository.delete(document);
        log("draft deleted", document);
    }

    @Override
    public LegalDocumentDTO activateDocument(Long id) {
        LegalDocument document = find(id);
        if (Boolean.TRUE.equals(document.getActive())) {
            return toDto(document, true);
        }
        // The other version leaves first: the database allows one version in force per document.
        documentRepository.deactivateOtherVersions(document.getDocumentType(), document.getId());
        boolean publishing = document.isDraft();
        if (publishing) {
            document.setPublishedAt(ZonedDateTime.now());
        }
        document.setActive(true);
        LegalDocument saved = documentRepository.save(document);
        log(publishing ? "published" : "reactivated", saved);
        return toDto(saved, true);
    }

    @Override
    public LegalDocumentDTO withdrawDocument(Long id) {
        LegalDocument document = find(id);
        if (!Boolean.TRUE.equals(document.getActive())) {
            return toDto(document, true);
        }
        document.setActive(false);
        LegalDocument saved = documentRepository.save(document);
        log("withdrawn", saved);
        return toDto(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public String preview(String content) {
        String normalized = LegalContent.normalize(content);
        if (normalized == null || normalized.isBlank()) return "";
        return renderer.render(checkedLength(normalized));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicLegalDocumentDTO findPublished(String type) {
        return toPublic(documentRepository.findFirstByDocumentTypeAndActiveTrue(publicType(type)).orElseThrow(this::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicLegalDocumentDTO findPublished(String type, String version) {
        return toPublic(
            documentRepository
                .findByDocumentTypeAndVersion(publicType(type), version)
                .filter(document -> !document.isDraft())
                .orElseThrow(this::notFound)
        );
    }

    private LegalStatusDTO buildStatus(String userId) {
        List<LegalDocument> documents = documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc();
        Set<Long> ids = documents.stream().map(LegalDocument::getId).collect(Collectors.toSet());
        Map<Long, UserLegalAcceptance> acceptances = findAcceptances(userId, ids);
        List<LegalDocumentStatusDTO> statuses = documents
            .stream()
            .map(document -> {
                UserLegalAcceptance acceptance = acceptances.get(document.getId());
                return new LegalDocumentStatusDTO(
                    document.getId(),
                    document.getDocumentType(),
                    document.getVersion(),
                    document.getTitle(),
                    document.isWithoutContent() ? document.getUrl() : null,
                    document.getContentSha256(),
                    document.getChangeSummary(),
                    document.getAction(),
                    document.getPublishedAt(),
                    Boolean.TRUE.equals(document.getRequired()),
                    acceptance != null,
                    acceptance == null ? null : acceptance.getAcceptedAt(),
                    acceptanceRepository.existsByUserIdAndDeletedFalseAndLegalDocument_DocumentTypeAndLegalDocument_IdNot(
                        userId,
                        document.getDocumentType(),
                        document.getId()
                    )
                );
            })
            .toList();
        boolean compliant = statuses.stream().noneMatch(document -> document.required() && !document.accepted());
        return new LegalStatusDTO(compliant, statuses);
    }

    private Map<Long, UserLegalAcceptance> findAcceptances(String userId, Set<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserLegalAcceptance> result = new HashMap<>();
        acceptanceRepository
            .findAllByUserIdAndLegalDocument_IdInAndDeletedFalse(userId, documentIds)
            .forEach(acceptance -> result.put(acceptance.getLegalDocument().getId(), acceptance));
        return result;
    }

    private void copyDraftFields(LegalDocumentDTO source, String version, LegalDocument target) {
        String content = validContent(source.getContent());
        target.setDocumentType(source.getDocumentType());
        target.setVersion(version);
        target.setTitle(source.getTitle().trim());
        target.setAction(source.getAction());
        target.setRequired(source.getRequired() == null || Boolean.TRUE.equals(source.getRequired()));
        target.setChangeSummary(blankToNull(source.getChangeSummary()));
        target.setContent(content);
        target.setContentSha256(LegalContent.sha256(content));
    }

    /** The details of a published document do not change while its text is added. */
    private boolean sameDetails(LegalDocument document, LegalDocumentDTO dto) {
        return (
            document.getDocumentType() == dto.getDocumentType() &&
            Objects.equals(document.getVersion(), trimmed(dto.getVersion())) &&
            Objects.equals(document.getTitle(), trimmed(dto.getTitle())) &&
            document.getAction() == dto.getAction() &&
            Objects.equals(document.getRequired(), dto.getRequired() == null || Boolean.TRUE.equals(dto.getRequired())) &&
            Objects.equals(blankToNull(document.getChangeSummary()), blankToNull(dto.getChangeSummary()))
        );
    }

    private String validVersion(String version) {
        String trimmed = trimmed(version);
        if (!LegalContent.isValidVersion(trimmed)) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "The version has characters that are not allowed", ENTITY_NAME, "legal.document.version.invalid");
        }
        return trimmed;
    }

    private String validContent(String content) {
        String normalized = LegalContent.normalize(content);
        if (normalized == null || normalized.isBlank()) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "The text of the document is empty", ENTITY_NAME, "legal.document.content.required");
        }
        return checkedLength(normalized);
    }

    private String checkedLength(String normalized) {
        if (normalized.length() > LegalContent.MAX_LENGTH) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "The text of the document is too long", ENTITY_NAME, "legal.document.content.tooLong");
        }
        return normalized;
    }

    private LegalDocumentType publicType(String type) {
        try {
            return LegalDocumentType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw notFound();
        }
    }

    private PublicLegalDocumentDTO toPublic(LegalDocument document) {
        List<PublicLegalDocumentDTO.Version> versions = documentRepository
            .findAllByDocumentTypeAndPublishedAtIsNotNullOrderByPublishedAtDesc(document.getDocumentType())
            .stream()
            .map(version -> new PublicLegalDocumentDTO.Version(version.getVersion(), version.getTitle(), version.getPublishedAt(), Boolean.TRUE.equals(version.getActive())))
            .toList();
        return new PublicLegalDocumentDTO(
            document.getDocumentType(),
            document.getVersion(),
            document.getTitle(),
            document.getAction(),
            document.getPublishedAt(),
            Boolean.TRUE.equals(document.getActive()),
            document.getChangeSummary(),
            document.isWithoutContent() ? null : renderer.render(document.getContent()),
            document.getContentSha256(),
            document.isWithoutContent() ? document.getUrl() : null,
            versions
        );
    }

    private LegalDocumentDTO toDto(LegalDocument document, boolean withContent) {
        LegalDocumentDTO dto = new LegalDocumentDTO();
        dto.setId(document.getId());
        dto.setDocumentType(document.getDocumentType());
        dto.setVersion(document.getVersion());
        dto.setTitle(document.getTitle());
        dto.setAction(document.getAction());
        dto.setRequired(document.getRequired());
        dto.setChangeSummary(document.getChangeSummary());
        dto.setContent(withContent ? document.getContent() : null);
        dto.setState(LegalDocumentState.of(document));
        dto.setPublishedAt(document.getPublishedAt());
        dto.setActive(document.getActive());
        dto.setContentSha256(document.getContentSha256());
        dto.setUrl(document.getUrl());
        return dto;
    }

    private LegalDocument find(Long id) {
        return documentRepository.findById(id).orElseThrow(this::notFound);
    }

    private RequestAlertException notFound() {
        return new RequestAlertException(HttpStatus.NOT_FOUND, "Legal document not found", ENTITY_NAME, "legal.document.notFound");
    }

    private RequestAlertException versionExists() {
        return new RequestAlertException(HttpStatus.CONFLICT, "This document version already exists", ENTITY_NAME, "legal.document.version.exists");
    }

    private RequestAlertException publishedImmutable() {
        return new RequestAlertException(
            HttpStatus.CONFLICT,
            "A published version is immutable; create a new version instead",
            ENTITY_NAME,
            "legal.document.published.immutable"
        );
    }

    private void log(String event, LegalDocument document) {
        LOG.info(
            "Legal document {} {}: type={}, version={}, sha256={}, by={}",
            document.getId(),
            event,
            document.getDocumentType(),
            document.getVersion(),
            document.getContentSha256(),
            actor()
        );
    }

    private static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken || authentication instanceof OAuth2AuthenticationToken) {
            String userId = SecurityUtils.getUserIdFromAuthentication((AbstractAuthenticationToken) authentication);
            if (userId != null) return userId;
        }
        return authentication == null ? "system" : authentication.getName();
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requiredTokenValue(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw "tenant".equals(claim)
                ? new RequestAlertException(HttpStatus.FORBIDDEN, "Missing tenant claim", ENTITY_NAME, "auth.tenant.missing")
                : new RequestAlertException(HttpStatus.UNAUTHORIZED, "Missing " + claim + " claim", ENTITY_NAME, "auth.identity.missing");
        }
        return value;
    }
}
