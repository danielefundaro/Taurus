package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.LegalDocument;
import com.fundaro.zodiac.taurus.domain.UserLegalAcceptance;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentState;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import com.fundaro.zodiac.taurus.repository.LegalDocumentRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.legal.LegalContent;
import com.fundaro.zodiac.taurus.service.legal.LegalMarkdownRenderer;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalServiceImplTest {

    private static final String TEXT = "## 1. Accesso\n\nL'accesso avviene tramite account.";

    @Mock
    private LegalDocumentRepository documentRepository;

    @Mock
    private UserLegalAcceptanceRepository acceptanceRepository;

    private LegalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LegalServiceImpl(documentRepository, acceptanceRepository, new LegalMarkdownRenderer());
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
        LegalDocument terms = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        LegalDocument privacy = published(2L, LegalDocumentType.PRIVACY, LegalDocumentAction.ACKNOWLEDGE);
        when(documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc()).thenReturn(List.of(terms, privacy));
        when(acceptanceRepository.findAllByUserIdAndLegalDocument_IdInAndDeletedFalse(eq("user-1"), anyCollection())).thenReturn(List.of());

        var status = service.getStatus(authentication());

        assertThat(status.compliant()).isFalse();
        assertThat(status.documents()).hasSize(2).allMatch(document -> document.required() && !document.accepted());
    }

    @Test
    void shouldGiveTheFingerprintAndHideTheUrlOfDocumentsWithText() {
        LegalDocument terms = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        terms.setChangeSummary("Nuova sezione sui minori.");
        LegalDocument privacy = withoutContent(2L, LegalDocumentType.PRIVACY);
        when(documentRepository.findAllByActiveTrueOrderByDocumentTypeAsc()).thenReturn(List.of(terms, privacy));
        when(acceptanceRepository.findAllByUserIdAndLegalDocument_IdInAndDeletedFalse(eq("user-1"), anyCollection())).thenReturn(List.of());
        when(acceptanceRepository.existsByUserIdAndDeletedFalseAndLegalDocument_DocumentTypeAndLegalDocument_IdNot("user-1", LegalDocumentType.TERMS, 1L)).thenReturn(true);

        var documents = service.getStatus(authentication()).documents();

        assertThat(documents.get(0).url()).isNull();
        assertThat(documents.get(0).contentSha256()).isEqualTo(LegalContent.sha256(TEXT));
        assertThat(documents.get(0).changeSummary()).isEqualTo("Nuova sezione sui minori.");
        assertThat(documents.get(0).previousVersionAccepted()).isTrue();
        assertThat(documents.get(1).url()).isEqualTo("https://example.test/privacy");
        assertThat(documents.get(1).contentSha256()).isNull();
        assertThat(documents.get(1).previousVersionAccepted()).isFalse();
    }

    @Test
    void shouldPersistVersionedConfirmationsWithTheirFingerprint() {
        LegalDocument terms = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        LegalDocument privacy = withoutContent(2L, LegalDocumentType.PRIVACY);
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
        assertThat(persisted).filteredOn(acceptance -> acceptance.getLegalDocument() == terms).singleElement().extracting(UserLegalAcceptance::getContentSha256).isEqualTo(LegalContent.sha256(TEXT));
        assertThat(persisted).filteredOn(acceptance -> acceptance.getLegalDocument() == privacy).singleElement().extracting(UserLegalAcceptance::getContentSha256).isNull();
    }

    @Test
    void shouldCreateADraftWithTheNormalizedTextAndItsFingerprint() {
        when(documentRepository.existsByDocumentTypeAndVersion(LegalDocumentType.TERMS, "1.3")).thenReturn(false);
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LegalDocumentDTO dto = dto(" 1.3 ", TEXT.replace("\n", "\r\n") + "\r\n\r\n");
        dto.setChangeSummary("  ");

        LegalDocumentDTO created = service.createDocument(dto);

        assertThat(created.getState()).isEqualTo(LegalDocumentState.DRAFT);
        assertThat(created.getVersion()).isEqualTo("1.3");
        assertThat(created.getContent()).isEqualTo(TEXT);
        assertThat(created.getContentSha256()).isEqualTo(LegalContent.sha256(TEXT));
        assertThat(created.getChangeSummary()).isNull();
        assertThat(created.getPublishedAt()).isNull();
        assertThat(created.getActive()).isFalse();
        assertThat(created.getUrl()).isNull();
    }

    @Test
    void shouldRejectAnInvalidDraft() {
        assertError(() -> service.createDocument(dto("1/3", TEXT)), "legal.document.version.invalid");
        when(documentRepository.existsByDocumentTypeAndVersion(any(), any())).thenReturn(false);
        assertError(() -> service.createDocument(dto("1.3", " \n ")), "legal.document.content.required");
        assertError(() -> service.createDocument(dto("1.3", "a".repeat(LegalContent.MAX_LENGTH + 1))), "legal.document.content.tooLong");
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldRejectAnExistingVersion() {
        when(documentRepository.existsByDocumentTypeAndVersion(LegalDocumentType.TERMS, "1.3")).thenReturn(true);

        assertError(() -> service.createDocument(dto("1.3", TEXT)), "legal.document.version.exists");
    }

    @Test
    void shouldChangeADraft() {
        LegalDocument draft = draft(5L);
        when(documentRepository.findById(5L)).thenReturn(Optional.of(draft));
        when(documentRepository.existsByDocumentTypeAndVersionAndIdNot(LegalDocumentType.TERMS, "1.4", 5L)).thenReturn(false);
        when(documentRepository.save(draft)).thenReturn(draft);

        LegalDocumentDTO updated = service.updateDocument(5L, dto("1.4", "Nuovo testo"));

        assertThat(updated.getVersion()).isEqualTo("1.4");
        assertThat(updated.getContent()).isEqualTo("Nuovo testo");
        assertThat(updated.getContentSha256()).isEqualTo(LegalContent.sha256("Nuovo testo"));
    }

    @Test
    void shouldNeverChangeAPublishedVersion() {
        LegalDocument terms = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(terms));

        assertError(() -> service.updateDocument(1L, dto("1.0", "Altro testo")), "legal.document.published.immutable");
        assertError(() -> service.updateDocument(1L, dto("1.0", TEXT)), "legal.document.published.immutable");
        assertError(() -> service.deleteDocument(1L), "legal.document.published.immutable");
        verify(documentRepository, never()).save(any());
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void shouldAddTheTextOfADocumentWithoutTextOnce() {
        LegalDocument privacy = withoutContent(2L, LegalDocumentType.PRIVACY);
        when(documentRepository.findById(2L)).thenReturn(Optional.of(privacy));
        when(documentRepository.save(privacy)).thenReturn(privacy);
        LegalDocumentDTO completion = sameDetails(privacy, TEXT);

        LegalDocumentDTO completed = service.updateDocument(2L, completion);

        assertThat(completed.getContent()).isEqualTo(TEXT);
        assertThat(completed.getContentSha256()).isEqualTo(LegalContent.sha256(TEXT));
        assertThat(completed.getUrl()).isEqualTo("https://example.test/privacy");
        assertThat(completed.getState()).isEqualTo(LegalDocumentState.ACTIVE);
        assertError(() -> service.updateDocument(2L, sameDetails(privacy, "Altro testo")), "legal.document.published.immutable");
    }

    @Test
    void shouldNotChangeTheDetailsWhileAddingTheText() {
        LegalDocument privacy = withoutContent(2L, LegalDocumentType.PRIVACY);
        when(documentRepository.findById(2L)).thenReturn(Optional.of(privacy));
        LegalDocumentDTO completion = sameDetails(privacy, TEXT);
        completion.setTitle("Altro titolo");

        assertError(() -> service.updateDocument(2L, completion), "legal.document.published.immutable");
    }

    @Test
    void shouldDeleteADraft() {
        LegalDocument draft = draft(5L);
        when(documentRepository.findById(5L)).thenReturn(Optional.of(draft));

        service.deleteDocument(5L);

        verify(documentRepository).delete(draft);
    }

    @Test
    void shouldPublishADraftAfterRetiringTheVersionInForce() {
        LegalDocument draft = draft(5L);
        when(documentRepository.findById(5L)).thenReturn(Optional.of(draft));
        when(documentRepository.save(draft)).thenReturn(draft);

        LegalDocumentDTO published = service.activateDocument(5L);

        InOrder order = inOrder(documentRepository);
        order.verify(documentRepository).deactivateOtherVersions(LegalDocumentType.TERMS, 5L);
        order.verify(documentRepository).save(draft);
        assertThat(published.getState()).isEqualTo(LegalDocumentState.ACTIVE);
        assertThat(published.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldReactivateARetiredVersionKeepingItsDate() {
        LegalDocument retired = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        ZonedDateTime publishedAt = retired.getPublishedAt();
        retired.setActive(false);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(retired));
        when(documentRepository.save(retired)).thenReturn(retired);

        LegalDocumentDTO reactivated = service.activateDocument(1L);

        assertThat(reactivated.getState()).isEqualTo(LegalDocumentState.ACTIVE);
        assertThat(reactivated.getPublishedAt()).isEqualTo(publishedAt);
        verify(documentRepository).deactivateOtherVersions(LegalDocumentType.TERMS, 1L);
    }

    @Test
    void shouldLeaveAVersionInForceAsItIs() {
        LegalDocument terms = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(terms));

        assertThat(service.activateDocument(1L).getState()).isEqualTo(LegalDocumentState.ACTIVE);
        verify(documentRepository, never()).deactivateOtherVersions(any(), any());
    }

    @Test
    void shouldWithdrawTheVersionInForce() {
        LegalDocument terms = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(terms));
        when(documentRepository.save(terms)).thenReturn(terms);

        assertThat(service.withdrawDocument(1L).getState()).isEqualTo(LegalDocumentState.RETIRED);
        assertThat(service.withdrawDocument(1L).getState()).isEqualTo(LegalDocumentState.RETIRED);
        verify(documentRepository).save(terms);
    }

    @Test
    void shouldListTheVersionsWithoutTheirText() {
        when(documentRepository.findAllByOrderByDocumentTypeAscPublishedAtDesc()).thenReturn(List.of(draft(5L), published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT)));

        List<LegalDocumentDTO> documents = service.findAllDocuments();

        assertThat(documents).extracting(LegalDocumentDTO::getState).containsExactly(LegalDocumentState.DRAFT, LegalDocumentState.ACTIVE);
        assertThat(documents).allMatch(document -> document.getContent() == null && document.getContentSha256() != null);
    }

    @Test
    void shouldPreviewTheText() {
        assertThat(service.preview("# Titolo")).isEqualTo("<h2 id=\"titolo\">Titolo</h2>");
        assertThat(service.preview(" ")).isEmpty();
        assertThat(service.preview(null)).isEmpty();
        assertError(() -> service.preview("a".repeat(LegalContent.MAX_LENGTH + 1)), "legal.document.content.tooLong");
    }

    @Test
    void shouldPublishTheVersionInForceWithTheOtherVersions() {
        LegalDocument terms = published(1L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        LegalDocument retired = published(3L, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT);
        retired.setVersion("0.9");
        retired.setActive(false);
        when(documentRepository.findFirstByDocumentTypeAndActiveTrue(LegalDocumentType.TERMS)).thenReturn(Optional.of(terms));
        when(documentRepository.findAllByDocumentTypeAndPublishedAtIsNotNullOrderByPublishedAtDesc(LegalDocumentType.TERMS)).thenReturn(List.of(terms, retired));

        var document = service.findPublished("terms");

        assertThat(document.html()).isEqualTo("<h2 id=\"1-accesso\">1. Accesso</h2>\n<p>L'accesso avviene tramite account.</p>");
        assertThat(document.contentSha256()).isEqualTo(LegalContent.sha256(TEXT));
        assertThat(document.url()).isNull();
        assertThat(document.active()).isTrue();
        assertThat(document.versions()).extracting(version -> version.version() + ":" + version.active()).containsExactly("1.0:true", "0.9:false");
    }

    @Test
    void shouldPublishTheAddressOfADocumentWithoutText() {
        LegalDocument privacy = withoutContent(2L, LegalDocumentType.PRIVACY);
        when(documentRepository.findByDocumentTypeAndVersion(LegalDocumentType.PRIVACY, "1.0")).thenReturn(Optional.of(privacy));
        when(documentRepository.findAllByDocumentTypeAndPublishedAtIsNotNullOrderByPublishedAtDesc(LegalDocumentType.PRIVACY)).thenReturn(List.of(privacy));

        var document = service.findPublished("PRIVACY", "1.0");

        assertThat(document.html()).isNull();
        assertThat(document.url()).isEqualTo("https://example.test/privacy");
    }

    @Test
    void shouldHideDraftsUnknownVersionsAndUnknownDocuments() {
        when(documentRepository.findByDocumentTypeAndVersion(LegalDocumentType.TERMS, "1.3")).thenReturn(Optional.of(draft(5L)));
        when(documentRepository.findByDocumentTypeAndVersion(LegalDocumentType.TERMS, "9")).thenReturn(Optional.empty());
        when(documentRepository.findFirstByDocumentTypeAndActiveTrue(LegalDocumentType.PRIVACY)).thenReturn(Optional.empty());

        assertError(() -> service.findPublished("terms", "1.3"), "legal.document.notFound");
        assertError(() -> service.findPublished("terms", "9"), "legal.document.notFound");
        assertError(() -> service.findPublished("privacy"), "legal.document.notFound");
        assertError(() -> service.findPublished("cookies"), "legal.document.notFound");
        assertError(() -> service.findPublished(null), "legal.document.notFound");
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String key) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo(key));
    }

    private static LegalDocumentDTO dto(String version, String content) {
        LegalDocumentDTO dto = new LegalDocumentDTO();
        dto.setDocumentType(LegalDocumentType.TERMS);
        dto.setVersion(version);
        dto.setTitle("Termini di utilizzo");
        dto.setAction(LegalDocumentAction.ACCEPT);
        dto.setRequired(true);
        dto.setContent(content);
        return dto;
    }

    private static LegalDocumentDTO sameDetails(LegalDocument document, String content) {
        LegalDocumentDTO dto = new LegalDocumentDTO();
        dto.setDocumentType(document.getDocumentType());
        dto.setVersion(document.getVersion());
        dto.setTitle(document.getTitle());
        dto.setAction(document.getAction());
        dto.setRequired(document.getRequired());
        dto.setContent(content);
        return dto;
    }

    private static LegalDocument draft(Long id) {
        return document(id, LegalDocumentType.TERMS, LegalDocumentAction.ACCEPT, draft -> {
            draft.setVersion("1.3");
            draft.setActive(false);
            draft.setPublishedAt(null);
        });
    }

    private static LegalDocument published(Long id, LegalDocumentType type, LegalDocumentAction action) {
        return document(id, type, action, document -> {});
    }

    private static LegalDocument withoutContent(Long id, LegalDocumentType type) {
        return document(id, type, LegalDocumentAction.ACKNOWLEDGE, document -> {
            document.setContent(null);
            document.setContentSha256(null);
            document.setUrl("https://example.test/" + type.name().toLowerCase());
        });
    }

    private static LegalDocument document(Long id, LegalDocumentType type, LegalDocumentAction action, Consumer<LegalDocument> customizer) {
        LegalDocument document = new LegalDocument();
        document.setId(id);
        document.setDocumentType(type);
        document.setVersion("1.0");
        document.setTitle(type.name());
        document.setContent(TEXT);
        document.setContentSha256(LegalContent.sha256(TEXT));
        document.setAction(action);
        document.setPublishedAt(ZonedDateTime.now().minusDays(1));
        document.setActive(true);
        document.setRequired(true);
        customizer.accept(document);
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
