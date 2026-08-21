package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.LegalDocument;
import com.fundaro.zodiac.taurus.domain.UserLegalAcceptance;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import com.fundaro.zodiac.taurus.repository.LegalDocumentRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class LegalServiceImplTest {

    @Mock
    private LegalDocumentRepository documentRepository;

    @Mock
    private UserLegalAcceptanceRepository acceptanceRepository;

    private LegalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LegalServiceImpl(documentRepository, acceptanceRepository);
    }

    @Test
    void shouldBeCompliantWhenThereAreNoActiveDocuments() {
        when(documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc()).thenReturn(List.of());

        var status = service.getStatus(authentication());

        assertThat(status.compliant()).isTrue();
        assertThat(status.documents()).isEmpty();
    }

    @Test
    void shouldRequireEveryActiveDocument() {
        LegalDocument terms = document(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        LegalDocument privacy = document(2L, LegalDocumentType.PRIVACY, LegalDocumentAction.ACKNOWLEDGE);
        when(documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc()).thenReturn(List.of(terms, privacy));
        when(acceptanceRepository.findAllByUserIdAndLegalDocument_IdInAndDeletedFalse(eq("user-1"), anyCollection())).thenReturn(List.of());

        var status = service.getStatus(authentication());

        assertThat(status.compliant()).isFalse();
        assertThat(status.documents()).hasSize(2).allMatch(document -> document.required() && !document.accepted());
    }

    @Test
    void shouldPersistVersionedConfirmationsWithTokenTenant() {
        LegalDocument terms = document(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        LegalDocument privacy = document(2L, LegalDocumentType.PRIVACY, LegalDocumentAction.ACKNOWLEDGE);
        List<UserLegalAcceptance> persisted = new ArrayList<>();
        when(documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc()).thenReturn(List.of(terms, privacy));
        when(acceptanceRepository.findAllByUserIdAndLegalDocument_IdInAndDeletedFalse(eq("user-1"), anyCollection())).thenAnswer(invocation -> persisted);
        when(acceptanceRepository.saveAll(any())).thenAnswer(invocation -> {
            persisted.addAll(invocation.getArgument(0));
            return persisted;
        });

        var status = service.accept(new LegalAcceptanceRequestDTO(Set.of(1L, 2L)), authentication());

        assertThat(status.compliant()).isTrue();
        assertThat(persisted)
            .hasSize(2)
            .allMatch(acceptance -> "user-1".equals(acceptance.getUserId()))
            .allMatch(acceptance -> acceptance.getAcceptedAt() != null);
    }

    private LegalDocument document(Long id, LegalDocumentType type, LegalDocumentAction action) {
        LegalDocument document = new LegalDocument();
        document.setId(id);
        document.setDocumentType(type);
        document.setVersion("1.0");
        document.setTitle(type.name());
        document.setUrl("https://example.test/" + type.name().toLowerCase());
        document.setAction(action);
        document.setPublishedAt(ZonedDateTime.now());
        document.setActive(true);
        document.setRequired(true);
        return document;
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", "tenant-a")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
