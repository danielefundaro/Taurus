package com.fundaro.zodiac.taurus.service.loansheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.inventory.*;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.*;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryLoanSheetServiceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Mock
    InventoryLoanSheetRepository sheetRepository;
    @Mock
    InventoryLoanSheetLineRepository lineRepository;
    @Mock
    InventoryLoanSheetCounterRepository counterRepository;
    @Mock
    InventoryAssignmentRenewalRepository renewalRepository;
    @Mock
    InventoryLoanSheetExportRepository exportRepository;
    @Mock
    InventoryAssignmentRepository assignmentRepository;
    @Mock
    InventoryAssignmentRevisionRepository revisionRepository;
    @Mock
    InventoryReturnRepository returnRepository;
    @Mock
    InventoryItemRepository itemRepository;
    @Mock
    InventoryLoanTemplateService templateService;
    @Mock
    InventoryLoanSheetPolicy policy;
    @Mock
    InventoryService inventoryService;
    @Mock
    UsersService usersService;
    @Mock
    LoanSheetTenantData tenantData;
    @Mock
    TenantTimeZoneService tenantTimeZoneService;
    @Mock
    MediaService mediaService;
    @Mock
    MediaRepository mediaRepository;

    private InventoryLoanSheetService service;
    private final LoanTemplateDefaults defaults = new LoanTemplateDefaults();
    private InventoryLoanSettings settings;
    private InventoryLoanTemplate template;

    @BeforeEach
    void setUp() {
        service = new InventoryLoanSheetService(
            sheetRepository,
            lineRepository,
            counterRepository,
            renewalRepository,
            exportRepository,
            assignmentRepository,
            revisionRepository,
            returnRepository,
            itemRepository,
            templateService,
            policy,
            inventoryService,
            usersService,
            tenantData,
            tenantTimeZoneService,
            mediaService,
            mediaRepository,
            new LoanSheetPdfRenderer(),
            new ObjectMapper()
        );
        settings = new InventoryLoanSettings();
        settings.setMinMonths(12);
        settings.setMaxMonths(24);
        settings.setRenewalMaxMonths(24);
        settings.setTotalMaxMonths(36);
        template = new InventoryLoanTemplate();
        template.setId(5L);
        template.setOrigin(InventoryLoanTemplateOrigin.DEFAULT);
        template.setDefaultLabel(defaults.label());
        template.setContent(defaults.content());
        template.setContentHash(defaults.contentHash());
        when(tenantTimeZoneService.currentZoneId()).thenReturn(ROME);
        when(templateService.settings(anyString())).thenReturn(settings);
        when(templateService.templateForIssue(any(), anyString())).thenReturn(template);
        when(templateService.currentContent(any())).thenReturn(defaults.content());
        when(templateService.currentLabel(any())).thenReturn("predefinito " + defaults.label());
        when(tenantData.dto(any())).thenReturn(Optional.empty());
        when(tenantData.forTemplate(anyString(), any())).thenReturn(LoanSheetPdfRendererTest.TENANT);
        when(sheetRepository.save(any(InventoryLoanSheet.class))).thenAnswer(invocation -> {
            InventoryLoanSheet sheet = invocation.getArgument(0);
            if (sheet.getId() == null) sheet.setId(100L);
            return sheet;
        });
        when(counterRepository.saveAndFlush(any(InventoryLoanSheetCounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issuesTheFirstSheetOfTheYearAndLinksTheAssignments() {
        InventoryAssignment instrument = assignment(1L, InventoryLoanType.FIXED_TERM, TODAY.plusMonths(24));
        InventoryAssignment uniform = assignment(2L, InventoryLoanType.PRECARIOUS, null);
        when(usersService.findOne(eq(42L), any())).thenReturn(Optional.of(member(LocalDate.of(1990, 1, 1))));
        when(assignmentRepository.findAllForUpdate(List.of(1L, 2L))).thenReturn(List.of(instrument, uniform));
        when(counterRepository.findForUpdate(2026)).thenReturn(Optional.empty());

        Detail detail = service.issue(42L, new IssueRequest(List.of(1L, 2L), TODAY, "Roma", null, "7", TODAY.minusDays(3), "Mario Bianchi"), token());

        ArgumentCaptor<InventoryLoanSheetLine> lines = ArgumentCaptor.forClass(InventoryLoanSheetLine.class);
        verify(lineRepository, times(2)).save(lines.capture());
        assertThat(lines.getAllValues()).extracting(InventoryLoanSheetLine::getLineNumber).containsExactly(1, 2);
        assertThat(lines.getAllValues().get(0).getLoanType()).isEqualTo(InventoryLoanType.FIXED_TERM);
        assertThat(lines.getAllValues().get(0).getAgreedUnitValue()).isEqualByComparingTo("650");
        assertThat(instrument.getLoanSheetId()).isEqualTo(100L);
        assertThat(uniform.getLoanSheetId()).isEqualTo(100L);
        assertThat(detail.summary().sheetNumber()).isEqualTo("2026/001");
        assertThat(detail.summary().holderRole()).isEqualTo(InventoryLoanHolderRole.MEMBER);
        assertThat(detail.summary().templateLabel()).isEqualTo("predefinito " + defaults.label());
        assertThat(detail.terms().templateId()).isEqualTo(5L);
        assertThat(detail.terms().totalMaxMonths()).isEqualTo(36);
        ArgumentCaptor<InventoryLoanSheet> sheet = ArgumentCaptor.forClass(InventoryLoanSheet.class);
        verify(sheetRepository).save(sheet.capture());
        assertThat(sheet.getValue().getTemplate()).isSameAs(template);
        assertThat(sheet.getValue().getTemplateHash()).isEqualTo(defaults.contentHash());
        assertThat(InventoryLoanTemplateService.durations(sheet.getValue())).isEqualTo(InventoryLoanTemplateService.durations(settings));
        verify(sheetRepository).lockTenantSheets();
    }

    @Test
    void refusesASecondIssuedSheetForTheSameMember() {
        InventoryLoanSheet open = new InventoryLoanSheet();
        open.setSheetNumber("2026/003");
        when(usersService.findOne(eq(42L), any())).thenReturn(Optional.of(member(LocalDate.of(1990, 1, 1))));
        when(sheetRepository.findFirstByUserIndexAndStatusAndDeletedFalse(42L, InventoryLoanSheetStatus.ISSUED)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.issue(42L, new IssueRequest(List.of(1L), TODAY, null, null, null, null, null), token()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanSheet.alreadyIssued");
                assertThat(exception.getArgs().values()).containsEntry("sheetNumber", "2026/003");
            });
        verify(lineRepository, never()).save(any());
    }

    @Test
    void refusesToIssueWithoutLoanTypeOrWithAnExpirationOutOfRange() {
        InventoryAssignment untyped = assignment(1L, null, null);
        InventoryAssignment tooLong = assignment(2L, InventoryLoanType.FIXED_TERM, TODAY.plusMonths(30));
        when(usersService.findOne(eq(42L), any())).thenReturn(Optional.of(member(LocalDate.of(1990, 1, 1))));
        when(assignmentRepository.findAllForUpdate(List.of(1L, 2L))).thenReturn(List.of(untyped, tooLong));

        assertThatThrownBy(() -> service.issue(42L, new IssueRequest(List.of(1L, 2L), TODAY, null, null, null, null, null), token()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanSheet.validation");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> issues = (List<Map<String, Object>>) exception.getArgs().values().get("issues");
                assertThat(issues).extracting(issue -> issue.get("code")).containsExactly("missingLoanType", "expirationOutOfRange");
            });
        verify(sheetRepository, never()).save(any());
    }

    @Test
    void requiresTheHolderRoleWhenTheBirthDateIsUnknown() {
        when(usersService.findOne(eq(42L), any())).thenReturn(Optional.of(member(null)));

        assertThatThrownBy(() -> service.issue(42L, new IssueRequest(List.of(1L), TODAY, null, null, null, null, null), token()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("socio maggiorenne o il genitore");
    }

    @Test
    void suggestsTheGuardianUntilTheEighteenthBirthday() {
        LocalDate birth = LocalDate.of(2008, 9, 29);

        assertThat(InventoryLoanSheetService.suggestedRole(birth, TODAY)).isEqualTo(InventoryLoanHolderRole.GUARDIAN);
        assertThat(InventoryLoanSheetService.suggestedRole(birth, TODAY.plusDays(1))).isEqualTo(InventoryLoanHolderRole.MEMBER);
        assertThat(InventoryLoanSheetService.suggestedRole(null, TODAY)).isNull();
    }

    @Test
    void closesOnlyWhenEveryGoodHasBeenReturned() {
        InventoryLoanSheet sheet = issuedSheet();
        InventoryAssignment outstanding = assignment(1L, InventoryLoanType.PRECARIOUS, null);
        when(sheetRepository.findForUpdate(100L)).thenReturn(Optional.of(sheet));
        when(assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(100L)).thenReturn(List.of(outstanding));

        assertThatThrownBy(() -> service.close(100L, new CloseRequest(InventoryLoanReturnOutcome.COMPLIANT, null, null, null), token()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("tutti i beni");
    }

    @Test
    void recordsChargesAtClosure() {
        InventoryLoanSheet sheet = issuedSheet();
        InventoryAssignment returned = assignment(1L, InventoryLoanType.PRECARIOUS, null);
        returned.setReturnedQuantity(1);
        when(sheetRepository.findForUpdate(100L)).thenReturn(Optional.of(sheet));
        when(assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(100L)).thenReturn(List.of(returned));

        assertThatThrownBy(() -> service.close(100L, new CloseRequest(InventoryLoanReturnOutcome.CHARGES, null, null, null), token()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("importo e termine");

        service.close(100L, new CloseRequest(InventoryLoanReturnOutcome.CHARGES, new BigDecimal("45"), TODAY.plusDays(30), "Anna Verdi"), token());

        assertThat(sheet.getStatus()).isEqualTo(InventoryLoanSheetStatus.CLOSED);
        assertThat(sheet.getChargeAmount()).isEqualByComparingTo("45");
        assertThat(sheet.getReceivedByName()).isEqualTo("Anna Verdi");
        assertThat(sheet.getClosedAt()).isNotNull();
    }

    @Test
    void settlementRemovesPersonalDataAndGeneratedPdfs() {
        InventoryLoanSheet sheet = issuedSheet();
        sheet.setStatus(InventoryLoanSheetStatus.CLOSED);
        sheet.setReturnOutcome(InventoryLoanReturnOutcome.COMPLIANT);
        Media media = new Media();
        media.setId(900L);
        InventoryLoanSheetExport export = new InventoryLoanSheetExport();
        export.setMediaAsset(media);
        when(sheetRepository.findForUpdate(100L)).thenReturn(Optional.of(sheet));
        when(exportRepository.findAllBySheet_IdAndMediaAssetIsNotNull(100L)).thenReturn(List.of(export));

        service.settle(100L, token());

        assertThat(sheet.getStatus()).isEqualTo(InventoryLoanSheetStatus.SETTLED);
        assertThat(sheet.getMemberBirthDate()).isNull();
        assertThat(sheet.getMemberEmail()).isNull();
        assertThat(sheet.getMemberName()).isEqualTo("Dati personali");
        assertThat(sheet.getPersonalDataPurgedAt()).isNotNull();
        assertThat(export.getMediaAsset()).isNull();
        verify(mediaService).deleteIfUnreferenced(900L, "tenant-a", "user-1");
    }

    @Test
    void settlesAutomaticallyOnlySheetsStillClosedWithoutCharges() {
        InventoryLoanSheet closed = issuedSheet();
        closed.setStatus(InventoryLoanSheetStatus.CLOSED);
        closed.setReturnOutcome(InventoryLoanReturnOutcome.COMPLIANT);
        InventoryLoanSheet alreadySettled = issuedSheet();
        alreadySettled.setId(101L);
        alreadySettled.setStatus(InventoryLoanSheetStatus.SETTLED);
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(30);
        when(sheetRepository.findAllByStatusAndReturnOutcomeAndClosedAtBeforeAndDeletedFalse(
            InventoryLoanSheetStatus.CLOSED, InventoryLoanReturnOutcome.COMPLIANT, cutoff
        )).thenReturn(List.of(closed, alreadySettled));
        when(sheetRepository.findForUpdate(100L)).thenReturn(Optional.of(closed));
        when(sheetRepository.findForUpdate(101L)).thenReturn(Optional.of(alreadySettled));

        service.settleClosedWithoutCharges(cutoff, "tenant-a", InventoryLoanSheetRetentionScheduler.SYSTEM_ACTOR);

        assertThat(closed.getStatus()).isEqualTo(InventoryLoanSheetStatus.SETTLED);
        assertThat(closed.getSettledBy()).isEqualTo(InventoryLoanSheetRetentionScheduler.SYSTEM_ACTOR);
        assertThat(closed.getPersonalDataPurgedAt()).isNotNull();
        assertThat(alreadySettled.getPersonalDataPurgedAt()).isNull();
    }

    @Test
    void gdprErasurePseudonymizesEverySheetOfTheMember() {
        InventoryLoanSheet sheet = issuedSheet();
        sheet.setStatus(InventoryLoanSheetStatus.CLOSED);
        when(sheetRepository.findAllByUserKeycloakIdAndDeletedFalse("member-1")).thenReturn(List.of(sheet));

        service.purgeForUser("member-1", "erased:abc", "tenant-a", "admin-1");

        assertThat(sheet.getUserKeycloakId()).isEqualTo("erased:abc");
        assertThat(sheet.getMemberEmail()).isNull();
        assertThat(sheet.getMemberBirthDate()).isNull();
        assertThat(sheet.getStatus()).isEqualTo(InventoryLoanSheetStatus.CLOSED);
        verify(exportRepository).findAllBySheet_IdAndMediaAssetIsNotNull(100L);
    }

    @Test
    void numbersSheetsProgressivelyWithinTheYear() {
        InventoryLoanSheetCounter counter = new InventoryLoanSheetCounter();
        counter.setSheetYear(2026);
        counter.setLastSequence(6);
        when(usersService.findOne(eq(42L), any())).thenReturn(Optional.of(member(LocalDate.of(1990, 1, 1))));
        when(assignmentRepository.findAllForUpdate(List.of(2L))).thenReturn(List.of(assignment(2L, InventoryLoanType.PRECARIOUS, null)));
        when(counterRepository.findForUpdate(2026)).thenReturn(Optional.of(counter));

        Detail detail = service.issue(42L, new IssueRequest(List.of(2L), TODAY, null, null, null, null, null), token());

        assertThat(detail.summary().sheetNumber()).isEqualTo("2026/007");
        assertThat(counter.getLastSequence()).isEqualTo(7);
    }

    @Test
    void refusesThePdfOfASettledSheet() {
        InventoryLoanSheet sheet = issuedSheet();
        sheet.setStatus(InventoryLoanSheetStatus.SETTLED);
        sheet.setPersonalDataPurgedAt(ZonedDateTime.now());
        when(sheetRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sheet));

        assertThatThrownBy(() -> service.pdf(100L, token()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("non è più disponibile");
        verifyNoInteractions(mediaService);
    }

    @Test
    void cancellationReleasesTheAssignments() {
        InventoryLoanSheet sheet = issuedSheet();
        InventoryAssignment linked = assignment(1L, InventoryLoanType.PRECARIOUS, null);
        when(sheetRepository.findForUpdate(100L)).thenReturn(Optional.of(sheet));
        when(assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(100L)).thenReturn(List.of(linked));

        service.cancel(100L, new CancelRequest("Emesso per errore"), token());

        assertThat(linked.getLoanSheetId()).isNull();
        assertThat(sheet.getStatus()).isEqualTo(InventoryLoanSheetStatus.CANCELLED);
        assertThat(sheet.getCancellationReason()).isEqualTo("Emesso per errore");
    }

    @Test
    void renewsWithinTheDurationsRegisteredOnTheSheet() {
        InventoryLoanSheet sheet = issuedSheet();
        settings.setRenewalMaxMonths(1);
        settings.setTotalMaxMonths(12);
        InventoryAssignment instrument = assignment(1L, InventoryLoanType.FIXED_TERM, TODAY.plusMonths(12));
        instrument.setAssignedAt(TODAY.minusMonths(6).atTime(10, 0).atZone(ROME));
        instrument.setLoanSheetId(100L);
        when(assignmentRepository.findForUpdate(1L)).thenReturn(Optional.of(instrument));
        when(sheetRepository.findForUpdate(100L)).thenReturn(Optional.of(sheet));
        when(assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(100L)).thenReturn(List.of(instrument));

        RenewalRequest tooLong = new RenewalRequest(TODAY.plusMonths(32), "12", TODAY.plusMonths(11), TODAY.plusMonths(11));
        assertThatThrownBy(() -> service.renew(1L, tooLong, token()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("inventory.renewal.totalLimitExceeded");
                assertThat(exception.getArgs().values()).containsEntry("months", 36);
            });

        service.renew(1L, new RenewalRequest(TODAY.plusMonths(30), "12", TODAY.plusMonths(11), TODAY.plusMonths(11)), token());

        verify(inventoryService).applyRenewal(eq(1L), eq(TODAY.plusMonths(30)), any());
        ArgumentCaptor<InventoryAssignmentRenewal> renewal = ArgumentCaptor.forClass(InventoryAssignmentRenewal.class);
        verify(renewalRepository).save(renewal.capture());
        assertThat(renewal.getValue().getPreviousExpirationDate()).isEqualTo(TODAY.plusMonths(12));
        assertThat(renewal.getValue().getSheetId()).isEqualTo(100L);
    }

    @Test
    void refusesToRenewPrecariousLoans() {
        InventoryLoanSheet sheet = issuedSheet();
        InventoryAssignment uniform = assignment(1L, InventoryLoanType.PRECARIOUS, null);
        uniform.setLoanSheetId(100L);
        when(assignmentRepository.findForUpdate(1L)).thenReturn(Optional.of(uniform));
        when(sheetRepository.findForUpdate(100L)).thenReturn(Optional.of(sheet));

        assertThatThrownBy(() -> service.renew(1L, new RenewalRequest(TODAY.plusMonths(30), "12", TODAY, TODAY), token()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("comodato con scadenza");
        verifyNoInteractions(inventoryService);
    }

    private InventoryLoanSheet issuedSheet() {
        InventoryLoanSheet sheet = new InventoryLoanSheet();
        sheet.setId(100L);
        sheet.setStatus(InventoryLoanSheetStatus.ISSUED);
        sheet.setSheetNumber("2026/001");
        sheet.setUserIndex(42L);
        sheet.setUserKeycloakId("member-1");
        sheet.setMemberName("Mario");
        sheet.setMemberLastName("Rossi");
        sheet.setMemberBirthDate(LocalDate.of(1990, 1, 1));
        sheet.setMemberEmail("mario@example.org");
        sheet.setHolderRole(InventoryLoanHolderRole.MEMBER);
        sheet.setIssuedOn(TODAY);
        sheet.setTemplate(template);
        sheet.setTemplateHash(template.getContentHash());
        sheet.setMinMonths(12);
        sheet.setMaxMonths(24);
        sheet.setRenewalMaxMonths(24);
        sheet.setTotalMaxMonths(36);
        return sheet;
    }

    private static InventoryAssignment assignment(Long id, InventoryLoanType loanType, LocalDate expiration) {
        InventoryItem item = new InventoryItem();
        item.setId(id * 10);
        item.setName("Oggetto " + id);
        item.setInventoryNumber("INV-" + id);
        item.setLoanType(loanType);
        item.setConditionStatus(InventoryCondition.GOOD);
        item.setEstimatedUnitValue(new BigDecimal("650"));
        item.setCurrency("EUR");
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(id);
        assignment.setItem(item);
        assignment.setUserIndex(42L);
        assignment.setUserKeycloakId("member-1");
        assignment.setAssignedQuantity(1);
        assignment.setAssignedAt(TODAY.atTime(10, 0).atZone(ROME));
        assignment.setExpirationDate(expiration);
        assignment.setLoanSheetId(null);
        return assignment;
    }

    private static UsersDTO member(LocalDate birthDate) {
        UsersDTO user = new UsersDTO();
        user.setId(42L);
        user.setName("Mario");
        user.setLastName("Rossi");
        user.setEmail("mario@example.org");
        user.setKeycloakId("member-1");
        user.setBirthDate(birthDate == null ? null : java.sql.Date.valueOf(birthDate));
        return user;
    }

    @Test
    void generatesThePdfWithTheTemplateOfTheSheetAfterAChangeOfTemplate() throws IOException {
        InventoryLoanSheet sheet = issuedSheet();
        InventoryLoanTemplate uploaded = new InventoryLoanTemplate();
        uploaded.setId(8L);
        uploaded.setVersionNumber(2);
        uploaded.setOrigin(InventoryLoanTemplateOrigin.CUSTOM);
        uploaded.setContent("<html><body><p>Modello del tenant {{foglio.numero}} per {{socio.nomeCognome}}, rinnovo fino a {{durata.mesiRinnovoMassimi}} mesi</p></body></html>");
        sheet.setTemplate(uploaded);
        MediaDTO media = new MediaDTO();
        media.setId(900L);
        when(sheetRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sheet));
        when(mediaService.store(any(), anyString(), anyString(), anyString(), any())).thenReturn(media);
        settings.setRenewalMaxMonths(6);

        PdfContent pdf = service.pdf(100L, token());

        assertThat(pdf.fileName()).isEqualTo("foglio-affidamento-2026-001.pdf");
        try (PDDocument document = Loader.loadPDF(pdf.bytes())) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Modello del tenant 2026/001 per Mario Rossi, rinnovo fino a 24 mesi");
        }
        verify(templateService, never()).currentContent(any());
        verify(exportRepository).save(any(InventoryLoanSheetExport.class));
    }

    private static JwtAuthenticationToken token() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
