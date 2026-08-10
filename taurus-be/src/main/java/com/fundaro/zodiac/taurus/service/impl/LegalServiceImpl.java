package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.LegalDocument;
import com.fundaro.zodiac.taurus.domain.UserLegalAcceptance;
import com.fundaro.zodiac.taurus.repository.LegalDocumentRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.LegalService;
import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentStatusDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalStatusDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LegalServiceImpl implements LegalService {

    private static final String ENTITY_NAME = "LegalDocument";

    private final LegalDocumentRepository documentRepository;
    private final UserLegalAcceptanceRepository acceptanceRepository;

    public LegalServiceImpl(LegalDocumentRepository documentRepository, UserLegalAcceptanceRepository acceptanceRepository) {
        this.documentRepository = documentRepository;
        this.acceptanceRepository = acceptanceRepository;
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
        String tenantCode = requiredTokenValue(SecurityUtils.getTenantIdFromAuthentication(authenticationToken), "tenant");
        List<LegalDocument> activeDocuments = documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc();
        Set<Long> activeIds = activeDocuments.stream().map(LegalDocument::getId).collect(java.util.stream.Collectors.toSet());

        if (!activeIds.containsAll(request.documentIds())) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "One or more legal documents are no longer active", ENTITY_NAME, "legal.document.stale");
        }

        Map<Long, UserLegalAcceptance> existing = findAcceptances(userId, activeIds);
        Set<Long> missingRequiredIds = activeDocuments
            .stream()
            .filter(document -> Boolean.TRUE.equals(document.getRequired()))
            .map(LegalDocument::getId)
            .filter(id -> !existing.containsKey(id))
            .collect(java.util.stream.Collectors.toSet());

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
                acceptance.setDeleted(false);
                acceptance.setInsertBy(userId);
                acceptance.setInsertDate(now);
                acceptance.setEditBy(userId);
                acceptance.setEditDate(now);
                acceptance.setUserId(userId);
                acceptance.setTenantCode(tenantCode);
                acceptances.add(acceptance);
            });
        acceptanceRepository.saveAll(acceptances);

        return buildStatus(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegalDocumentDTO> findAllDocuments() {
        return documentRepository.findAllByOrderByDocumentTypeAscPublishedAtDesc().stream().map(this::toDto).toList();
    }

    @Override
    public LegalDocumentDTO createDocument(LegalDocumentDTO dto) {
        if (dto.getId() != null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "A new legal document cannot already have an id", ENTITY_NAME, "id.exists");
        }
        if (documentRepository.existsByDocumentTypeAndVersion(dto.getDocumentType(), dto.getVersion())) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "This document version already exists", ENTITY_NAME, "legal.document.version.exists");
        }
        LegalDocument document = new LegalDocument();
        copyMutableFields(dto, document);
        activateExclusively(document);
        return toDto(documentRepository.save(document));
    }

    @Override
    public LegalDocumentDTO updateDocument(Long id, LegalDocumentDTO dto) {
        LegalDocument document = documentRepository
            .findById(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Legal document not found", ENTITY_NAME, "legal.document.notFound"));

        if (
            documentRepository.existsByDocumentTypeAndVersionAndIdNot(dto.getDocumentType(), dto.getVersion(), id)
        ) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "This document version already exists", ENTITY_NAME, "legal.document.version.exists");
        }

        if (acceptanceRepository.existsByLegalDocument_Id(id) && contentChanged(document, dto)) {
            throw new RequestAlertException(
                HttpStatus.CONFLICT,
                "An accepted document is immutable; create a new version instead",
                ENTITY_NAME,
                "legal.document.accepted.immutable"
            );
        }
        copyMutableFields(dto, document);
        activateExclusively(document);
        return toDto(documentRepository.save(document));
    }

    private LegalStatusDTO buildStatus(String userId) {
        List<LegalDocument> documents = documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc();
        Set<Long> ids = documents.stream().map(LegalDocument::getId).collect(java.util.stream.Collectors.toSet());
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
                    document.getUrl(),
                    document.getAction(),
                    document.getPublishedAt(),
                    Boolean.TRUE.equals(document.getRequired()),
                    acceptance != null,
                    acceptance == null ? null : acceptance.getAcceptedAt()
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

    private void activateExclusively(LegalDocument document) {
        if (Boolean.TRUE.equals(document.getActive())) {
            documentRepository.deactivateOtherVersions(document.getDocumentType(), document.getId());
        }
    }

    private void copyMutableFields(LegalDocumentDTO source, LegalDocument target) {
        target.setDocumentType(source.getDocumentType());
        target.setVersion(source.getVersion().trim());
        target.setTitle(source.getTitle().trim());
        target.setUrl(source.getUrl().trim());
        target.setAction(source.getAction());
        target.setPublishedAt(source.getPublishedAt());
        target.setActive(Boolean.TRUE.equals(source.getActive()));
        target.setRequired(source.getRequired() == null || Boolean.TRUE.equals(source.getRequired()));
    }

    private boolean contentChanged(LegalDocument document, LegalDocumentDTO dto) {
        return (
            document.getDocumentType() != dto.getDocumentType() ||
            !Objects.equals(document.getVersion(), dto.getVersion().trim()) ||
            !Objects.equals(document.getTitle(), dto.getTitle().trim()) ||
            !Objects.equals(document.getUrl(), dto.getUrl().trim()) ||
            document.getAction() != dto.getAction() ||
            !sameInstant(document.getPublishedAt(), dto.getPublishedAt()) ||
            !Objects.equals(document.getRequired(), dto.getRequired() == null || Boolean.TRUE.equals(dto.getRequired()))
        );
    }

    private LegalDocumentDTO toDto(LegalDocument document) {
        LegalDocumentDTO dto = new LegalDocumentDTO();
        dto.setId(document.getId());
        dto.setDocumentType(document.getDocumentType());
        dto.setVersion(document.getVersion());
        dto.setTitle(document.getTitle());
        dto.setUrl(document.getUrl());
        dto.setAction(document.getAction());
        dto.setPublishedAt(document.getPublishedAt());
        dto.setActive(document.getActive());
        dto.setRequired(document.getRequired());
        return dto;
    }

    private boolean sameInstant(ZonedDateTime first, ZonedDateTime second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.toInstant().equals(second.toInstant());
    }

    private String requiredTokenValue(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Missing " + claim + " claim", ENTITY_NAME, "legal.token.claim.missing");
        }
        return value;
    }
}
