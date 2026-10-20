package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSettings;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplate;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRenewalRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSettingsRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRepository;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.HolderDataRequest;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.HolderData;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContentFactory;
import com.fundaro.zodiac.taurus.service.loansheet.LoanTemplateDefaults;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanSheetSignatureServiceTest {

    private static final String TEMPLATE = new LoanTemplateDefaults().content();

    @Mock
    SignatureActRepository actRepository;
    @Mock
    SignatureActSignerRepository signerRepository;
    @Mock
    SignatureDocumentRepository documentRepository;
    @Mock
    SignatureEvidenceRepository evidenceRepository;
    @Mock
    SignatureEvidenceService evidenceService;
    @Mock
    InventoryLoanSheetRepository sheetRepository;
    @Mock
    InventoryLoanSettingsRepository settingsRepository;
    @Mock
    InventoryAssignmentRepository assignmentRepository;
    @Mock
    InventoryAssignmentRenewalRepository renewalRepository;
    @Mock
    InventoryReturnRepository returnRepository;
    @Mock
    UsersRepository usersRepository;
    @Mock
    LoanSheetContentFactory contentFactory;
    @Mock
    LoanSheetActDocuments documents;
    @Mock
    SignatureLinkService linkService;
    @Mock
    SignatureLinkRepository linkRepository;
    @Mock
    TenantsService tenantsService;
    @Mock
    CieSignVerifier cieSignVerifier;
    @Mock
    SignatureMetrics metrics;

    private LoanSheetSignatureService service;
    private final InventoryLoanSettings settings = new InventoryLoanSettings();

    @BeforeEach
    void setUp() {
        service = new LoanSheetSignatureService(
            actRepository,
            signerRepository,
            documentRepository,
            evidenceRepository,
            evidenceService,
            sheetRepository,
            settingsRepository,
            assignmentRepository,
            renewalRepository,
            returnRepository,
            usersRepository,
            contentFactory,
            documents,
            linkService,
            linkRepository,
            tenantsService,
            cieSignVerifier,
            metrics,
            new ApplicationProperties()
        );
        settings.setElectronicSignatureEnabled(true);
        when(settingsRepository.findFirstByDeletedFalse()).thenReturn(Optional.of(settings));
        when(usersRepository.findActiveKeycloakIdsByRolesIn(anyCollection())).thenReturn(List.of("admin-1", "member-1"));
        when(usersRepository.findByKeycloakIdAndDeletedFalse("admin-1")).thenReturn(Optional.of(user("admin-1", "Anna", "Bianchi")));
        when(usersRepository.findByKeycloakIdAndDeletedFalse("member-1")).thenReturn(Optional.of(user("member-1", "Mario", "Rossi")));
    }

    @Test
    void checksAnElectronicIssueAndReturnsWhoSignsForTheAssociation() {
        assertThat(service.checkIssue(InventoryLoanHolderRole.MEMBER, "member-1", "mario@example.org", "admin-1", TEMPLATE))
            .isEqualTo(new LoanSheetSignatureService.AssociationSigner("admin-1", "Anna Bianchi"));

        assertThat(service.checkIssue(InventoryLoanHolderRole.GUARDIAN, "member-1", "genitore@example.org", "admin-1", TEMPLATE).userId()).isEqualTo("admin-1");
        assertKey(() -> service.checkIssue(InventoryLoanHolderRole.GUARDIAN, "member-1", null, "admin-1", TEMPLATE), "inventory.loanSheet.holderEmailRequired");
        assertKey(() -> service.checkIssue(InventoryLoanHolderRole.MEMBER, "member-1", null, "admin-1", TEMPLATE), "inventory.loanSheet.holderEmailRequired");
        assertKey(() -> service.checkIssue(InventoryLoanHolderRole.MEMBER, "member-1", "mario@example.org", "admin-1", "<body><p>x</p></body>"), "inventory.loanSheet.templateNotSignable");
        assertKey(() -> service.checkIssue(InventoryLoanHolderRole.MEMBER, "member-1", "mario@example.org", "member-1", TEMPLATE), "signature.associationSigner.sameAsHolder");
        assertKey(() -> service.checkIssue(InventoryLoanHolderRole.MEMBER, "member-1", "mario@example.org", "someone", TEMPLATE), "signature.associationSigner.notAdmin");

        settings.setElectronicSignatureEnabled(false);
        assertKey(() -> service.checkIssue(InventoryLoanHolderRole.MEMBER, "member-1", "mario@example.org", "admin-1", TEMPLATE), "inventory.loanSheet.signatureDisabled");
    }

    @Test
    void listsTheAdministratorsWhoCanSignForTheAssociationWithoutTheHolder() {
        assertThat(service.associationSigners("member-1")).extracting(option -> option.userId()).containsExactly("admin-1");
        assertThat(service.issueOptions("member-1", TEMPLATE, "admin-1").defaultAssociationSignerId()).isEqualTo("admin-1");
        assertThat(service.issueOptions("member-1", "<body></body>", "other").templateSignable()).isFalse();
    }

    @Test
    void checksTheHolderData() {
        HolderData data = LoanSheetSignatureService.holderData(request("rssmra85t10a562s", false, LocalDate.now().minusDays(1), null), InventoryLoanHolderRole.MEMBER, null);
        assertThat(data.taxCode()).isEqualTo("RSSMRA85T10A562S");
        assertThat(data.minorBirthPlace()).isNull();
        assertThat(data.holderName()).isNull();

        assertThat(LoanSheetSignatureService.holderData(request(null, true, LocalDate.now(), null), InventoryLoanHolderRole.MEMBER, null).taxCode()).isNull();

        assertThatThrownBy(() -> LoanSheetSignatureService.holderData(
            new HolderDataRequest(" ", "Via Roma 1", "001", "Roma", "RSSMRA85T10A562X", false, "CI", "Comune", LocalDate.now().plusDays(1), null, null),
            InventoryLoanHolderRole.GUARDIAN,
            "genitore@example.org"
        )).isInstanceOfSatisfying(RequestAlertException.class, exception -> {
            assertThat(exception.getErrorKey()).isEqualTo("signature.holderData.invalid");
            assertThat(exception.getArgs().values()).containsEntry(
                "fields",
                List.of("holderFirstName", "holderLastName", "holderBirthDate", "birthPlace", "postalCode", "taxCode", "documentIssuedOn", "minorBirthPlace")
            );
        });
    }

    @Test
    void checksTheDataOfAParentWhoMustBeAnAdult() {
        HolderData data = LoanSheetSignatureService.holderData(
            guardianRequest("Giulia", " Rossi ", LocalDate.now().minusYears(40)),
            InventoryLoanHolderRole.GUARDIAN,
            "genitore@example.org"
        );
        assertThat(data.holderName()).isEqualTo("Giulia Rossi");
        assertThat(data.holderEmail()).isEqualTo("genitore@example.org");
        assertThat(data.minorBirthPlace()).isEqualTo("Napoli");

        assertThatThrownBy(() -> LoanSheetSignatureService.holderData(
            guardianRequest("Giulia", "Rossi", LocalDate.now().minusYears(17)),
            InventoryLoanHolderRole.GUARDIAN,
            "genitore@example.org"
        )).isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getArgs().values()).containsEntry("fields", List.of("holderBirthDate")));
    }

    @Test
    void issuesTheActOfAParentWithTheLinkAndTheAcknowledgmentOfTheMinor() {
        when(actRepository.saveAndFlush(any(SignatureAct.class))).thenAnswer(invocation -> {
            SignatureAct act = invocation.getArgument(0);
            act.setId(10L);
            return act;
        });
        List<SignatureActSigner> saved = new ArrayList<>();
        when(signerRepository.saveAndFlush(any(SignatureActSigner.class))).thenAnswer(invocation -> {
            SignatureActSigner signer = invocation.getArgument(0);
            signer.setId(20L + saved.size());
            saved.add(signer);
            return signer;
        });

        LoanSheetActChange change = service.createIssueAct(guardianSheet(), new LoanSheetSignatureService.AssociationSigner("admin-1", "Anna Bianchi"), "admin-1");

        assertThat(saved)
            .extracting(SignatureActSigner::getSignerRole, SignatureActSigner::getUserKeycloakId, SignatureActSigner::getEmail, SignatureActSigner::isRequired)
            .containsExactly(
                tuple(SignerRole.HOLDER, null, "genitore@example.org", true),
                tuple(SignerRole.ASSOCIATION, "admin-1", null, true),
                tuple(SignerRole.MINOR, "member-1", null, false)
            );
        assertThat(saved.get(0).getDisplayName()).isEqualTo("Genitore o tutore di Luca Rossi");
        assertThat(change.recipients()).isEmpty();
        verify(linkService).createAndSendAfterCommit(
            eq(saved.get(0)),
            eq(new SignatureLinkService.LinkContent("2026/007", "Emissione del foglio", "Luca Rossi")),
            eq(false),
            eq("admin-1")
        );
    }

    @Test
    void expectsTwoConfirmationsFromTheHolderOfTheIssueAndOneFromTheOthers() {
        SignatureAct issue = act(SignatureActType.LOAN_SHEET_ISSUE, SignatureActStatus.AWAITING_SIGNATURES);
        SignatureAct variation = act(SignatureActType.LOAN_SHEET_VARIATION, SignatureActStatus.AWAITING_SIGNATURES);

        assertThat(LoanSheetSignatureService.pendingPurposes(issue, signer(SignerRole.HOLDER), Set.of()))
            .containsExactly(SignaturePurpose.ACCEPTANCE, SignaturePurpose.CLAUSE_APPROVAL);
        assertThat(LoanSheetSignatureService.pendingPurposes(issue, signer(SignerRole.HOLDER), EnumSet.of(SignaturePurpose.ACCEPTANCE)))
            .containsExactly(SignaturePurpose.CLAUSE_APPROVAL);
        assertThat(LoanSheetSignatureService.pendingPurposes(variation, signer(SignerRole.HOLDER), Set.of())).containsExactly(SignaturePurpose.ACCEPTANCE);
        assertThat(LoanSheetSignatureService.pendingPurposes(variation, signer(SignerRole.ASSOCIATION), Set.of())).containsExactly(SignaturePurpose.ASSOCIATION_SIGNATURE);
        assertThat(LoanSheetSignatureService.pendingPurposes(act(SignatureActType.LOAN_SHEET_ISSUE, SignatureActStatus.AWAITING_DATA), signer(SignerRole.HOLDER), Set.of())).isEmpty();
        assertThat(LoanSheetSignatureService.pendingPurposes(issue, signer(SignerRole.MINOR), Set.of())).containsExactly(SignaturePurpose.MINOR_ACKNOWLEDGMENT);
        assertThat(LoanSheetSignatureService.pendingPurposes(variation, signer(SignerRole.MINOR), Set.of())).isEmpty();
    }

    private static HolderDataRequest request(String taxCode, boolean noTaxCode, LocalDate issuedOn, String minorBirthPlace) {
        return new HolderDataRequest("Roma", "Via Roma 1", "00100", "Roma (RM)", taxCode, noTaxCode, "Carta d'identità", "Comune di Roma", issuedOn, null, minorBirthPlace);
    }

    private static HolderDataRequest guardianRequest(String firstName, String lastName, LocalDate birthDate) {
        return new HolderDataRequest(
            "Roma",
            "Via Roma 1",
            "00100",
            "Roma (RM)",
            null,
            true,
            "Carta d'identità",
            "Comune di Roma",
            LocalDate.now().minusYears(1),
            null,
            "Napoli",
            firstName,
            lastName,
            birthDate
        );
    }

    private static InventoryLoanSheet guardianSheet() {
        InventoryLoanTemplate template = new InventoryLoanTemplate();
        template.setId(3L);
        InventoryLoanSheet sheet = new InventoryLoanSheet();
        sheet.setId(7L);
        sheet.setSheetNumber("2026/007");
        sheet.setTemplate(template);
        sheet.setTemplateHash("b".repeat(64));
        sheet.setHolderRole(InventoryLoanHolderRole.GUARDIAN);
        sheet.setUserKeycloakId("member-1");
        sheet.setMemberName("Luca");
        sheet.setMemberLastName("Rossi");
        sheet.setHolderEmail("genitore@example.org");
        return sheet;
    }

    private static SignatureAct act(SignatureActType type, SignatureActStatus status) {
        SignatureAct act = new SignatureAct();
        act.setActType(type);
        act.setStatus(status);
        return act;
    }

    private static SignatureActSigner signer(SignerRole role) {
        SignatureActSigner signer = new SignatureActSigner();
        signer.setSignerRole(role);
        return signer;
    }

    private static Users user(String keycloakId, String name, String lastName) {
        Users user = new Users();
        user.setKeycloakId(keycloakId);
        user.setName(name);
        user.setLastName(lastName);
        return user;
    }

    private static void assertKey(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String key) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo(key));
    }
}
