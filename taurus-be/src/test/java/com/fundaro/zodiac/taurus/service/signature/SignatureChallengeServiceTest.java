package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.signature.*;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChallengeRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.*;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignatureChallengeServiceTest {

    private static final List<ChallengeItem> ITEMS = List.of(new ChallengeItem(5L, "a".repeat(64), InventoryDecisionType.ACCEPTED, null));
    private static final SignaturePurposeHandler.Prepared PREPARED = new SignaturePurposeHandler.Prepared(
        "[{\"assignmentId\":5}]",
        "Presa visione dell'assegnazione di «Clarinetto»",
        "aaaaaaaa",
        List.of("Ho preso visione dei dati e li accetto.")
    );

    @Mock
    SignaturePurposeHandler handler;
    @Mock
    SignaturePurposeHandler acceptanceHandler;
    @Mock
    SignatureChallengeRepository challengeRepository;
    @Mock
    SignatureActSignerRepository signerRepository;
    @Mock
    SignatureLinkService linkService;
    @Mock
    SignatureMailService mailService;
    @Mock
    KeycloakOtpClient otpClient;
    @Mock
    SignatureRequirementsService requirementsService;
    @Mock
    UsersRepository usersRepository;
    @Mock
    PreferencesRepository preferencesRepository;
    @Mock
    TenantsService tenantsService;
    @Mock
    TenantTimeZoneService tenantTimeZoneService;
    @Mock
    InventoryService inventoryService;
    @Mock
    PlatformTransactionManager transactionManager;

    private final Map<Long, SignatureChallenge> store = new HashMap<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private SignatureChallengeService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(handler.purpose()).thenReturn(SignaturePurpose.ASSIGNMENT_DECISION);
        when(handler.prepare(anyList(), any(Signer.class))).thenReturn(PREPARED);
        when(handler.describe(anyString())).thenReturn(PREPARED);
        when(acceptanceHandler.purpose()).thenReturn(SignaturePurpose.ACCEPTANCE);
        when(acceptanceHandler.prepare(anyList(), any(Signer.class))).thenReturn(PREPARED);
        when(acceptanceHandler.describe(anyString())).thenReturn(PREPARED);
        SignatureActSigner parent = new SignatureActSigner();
        parent.setId(30L);
        parent.setActId(10L);
        parent.setSignerRole(SignerRole.HOLDER);
        parent.setDisplayName("Giulia Rossi");
        parent.setEmail("genitore@example.org");
        when(signerRepository.findById(30L)).thenReturn(Optional.of(parent));
        when(challengeRepository.findRecentLinkCreations(anyLong(), any())).thenReturn(List.of());
        when(requirementsService.requirements()).thenReturn(new Requirements(true, true, true));
        Users user = new Users();
        user.setName("Mario");
        user.setLastName("Rossi");
        user.setEmail("mario.rossi@example.org");
        when(usersRepository.findByKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(user));
        TenantsDTO tenant = new TenantsDTO();
        tenant.setName("Banda musicale di prova");
        when(tenantsService.findByCode(eq("tenant-a"), any())).thenReturn(Optional.of(tenant));
        when(tenantTimeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        when(challengeRepository.findRecentCreations(anyString(), any())).thenReturn(List.of());
        when(challengeRepository.saveAndFlush(any(SignatureChallenge.class))).thenAnswer(invocation -> store(invocation.getArgument(0)));
        when(challengeRepository.save(any(SignatureChallenge.class))).thenAnswer(invocation -> store(invocation.getArgument(0)));
        when(challengeRepository.findForUpdate(anyLong())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.<Long>getArgument(0))));

        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().setOtpSecret("secret");
        service = new SignatureChallengeService(
            List.of(handler, acceptanceHandler),
            challengeRepository,
            new SignatureSigners(usersRepository, signerRepository, linkService),
            linkService,
            new SignatureCodes(properties),
            mailService,
            otpClient,
            requirementsService,
            usersRepository,
            preferencesRepository,
            tenantsService,
            tenantTimeZoneService,
            inventoryService,
            new SignatureMetrics(registry),
            properties,
            transactionManager
        );
    }

    @Test
    void sendsTheCodeAndStoresOnlyItsDigest() {
        Challenge challenge = service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1"));

        SignatureChallenge stored = store.get(challenge.id());
        String code = sentCode();
        assertThat(stored.getStatus()).isEqualTo(SignatureChallengeStatus.OPEN);
        assertThat(stored.getUserKeycloakId()).isEqualTo("user-1");
        assertThat(stored.getDestinationEmail()).isEqualTo("mario.rossi@example.org");
        assertThat(stored.getItemsHash()).isEqualTo(SignatureSupport.sha256(PREPARED.itemsJson()));
        assertThat(stored.getCodeDigest()).hasSize(64).isNotEqualTo(code);
        assertThat(stored.getSends()).isEqualTo(1);
        assertThat(challenge.maskedEmail()).isEqualTo("m***@example.org");
        assertThat(challenge.reference()).isEqualTo("aaaaaaaa");
        assertThat(challenge.declarations()).isEqualTo(PREPARED.declarations());
        assertThat(challenge.remainingAttempts()).isEqualTo(5);
        assertThat(challenge.remainingSends()).isEqualTo(4);
        ArgumentCaptor<SignatureMailService.CodeMessage> captor = ArgumentCaptor.forClass(SignatureMailService.CodeMessage.class);
        verify(mailService).sendCode(captor.capture());
        assertThat(captor.getValue().tenantName()).isEqualTo("Banda musicale di prova");
        assertThat(captor.getValue().destination()).isEqualTo("mario.rossi@example.org");
    }

    @Test
    void refusesTheAuthenticationAppUntilItIsConfigured() {
        assertError(() -> service.create(request(SignatureMethod.TOTP), http(), token("user-1")), "signature.method.totpNotConfigured");

        when(otpClient.configured()).thenReturn(true);
        when(otpClient.status("user-1")).thenReturn(KeycloakOtpClient.Status.NOT_CONFIGURED);
        assertError(() -> service.create(request(SignatureMethod.TOTP), http(), token("user-1")), "signature.method.totpNotConfigured");

        when(otpClient.status("user-1")).thenReturn(KeycloakOtpClient.Status.UNAVAILABLE);
        assertThatThrownBy(() -> service.create(request(SignatureMethod.TOTP), http(), token("user-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.method.unavailable");
                assertThat(exception.getStatusCode().value()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
            });
        assertThat(store).isEmpty();
        verify(mailService, never()).sendCode(any());
    }

    @Test
    void confirmsWithTheAuthenticationAppOfTheSignerWithoutSendingMail() {
        configureApp();
        when(otpClient.verify("user-1", "123456")).thenReturn(KeycloakOtpClient.Verification.VALID);
        when(handler.confirm(any(SignatureChallenge.class), any(Signer.class))).thenReturn(List.of());

        Challenge challenge = service.create(request(SignatureMethod.TOTP), http(), token("user-1"));

        SignatureChallenge stored = store.get(challenge.id());
        assertThat(stored.getMethod()).isEqualTo(SignatureMethod.TOTP);
        assertThat(stored.getDestinationEmail()).isNull();
        assertThat(stored.getCodeDigest()).isNull();
        assertThat(stored.getSends()).isZero();
        assertThat(challenge.maskedEmail()).isNull();
        assertThat(challenge.remainingSends()).isZero();
        assertThat(challenge.resendAvailableAt()).isNull();
        verify(mailService, never()).sendCode(any());

        service.confirm(challenge.id(), new ConfirmRequest("123456"), http(), token("user-1"));

        assertThat(stored.getStatus()).isEqualTo(SignatureChallengeStatus.CONFIRMED);
        verify(otpClient).verify("user-1", "123456");
        assertError(() -> service.resend(challenge.id(), token("user-1")), "signature.challenge.invalid");
    }

    @Test
    void consumesAnAttemptForAWrongCodeOfTheApp() {
        configureApp();
        when(otpClient.verify("user-1", "000000")).thenReturn(KeycloakOtpClient.Verification.INVALID);
        Challenge challenge = service.create(request(SignatureMethod.TOTP), http(), token("user-1"));

        assertThatThrownBy(() -> service.confirm(challenge.id(), new ConfirmRequest("000000"), http(), token("user-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.challenge.invalidCode");
                assertThat(exception.getArgs().values()).containsEntry("remainingAttempts", 4);
            });
        assertThat(store.get(challenge.id()).getAttempts()).isEqualTo(1);
        verify(handler, never()).confirm(any(), any());
    }

    @Test
    void keepsTheChallengeOpenWhenKeycloakDoesNotAnswer() {
        configureApp();
        when(otpClient.verify("user-1", "123456")).thenReturn(KeycloakOtpClient.Verification.UNAVAILABLE);
        Challenge challenge = service.create(request(SignatureMethod.TOTP), http(), token("user-1"));

        assertError(() -> service.confirm(challenge.id(), new ConfirmRequest("123456"), http(), token("user-1")), "signature.method.unavailable");

        assertThat(store.get(challenge.id()).getStatus()).isEqualTo(SignatureChallengeStatus.OPEN);
        assertThat(store.get(challenge.id()).getAttempts()).isZero();
        verify(handler, never()).confirm(any(), any());
    }

    @Test
    void refusesWhenTheInstallationLacksTheRequirements() {
        when(requirementsService.requirements()).thenReturn(new Requirements(false, true, true));

        assertThatThrownBy(() -> service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.settings.requirementsMissing");
                assertThat(exception.getArgs().values()).containsEntry("missing", List.of("mail"));
            });
    }

    @Test
    void requiresAnEmailAddress() {
        when(usersRepository.findByKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(new Users()));

        assertError(() -> service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1")), "signature.mail.addressMissing");
    }

    @Test
    void cancelsTheChallengeWhenTheCodeCannotBeSent() {
        doThrow(new MailSendException("refused")).when(mailService).sendCode(any());

        assertThatThrownBy(() -> service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.mail.sendFailed");
                assertThat(exception.getStatusCode().value()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
            });
        assertThat(store.values()).singleElement().extracting(SignatureChallenge::getStatus).isEqualTo(SignatureChallengeStatus.CANCELLED);
    }

    @Test
    void limitsTheCodesAUserCanRequestInAnHour() {
        when(challengeRepository.findRecentCreations(eq("user-1"), any()))
            .thenReturn(Collections.nCopies(20, ZonedDateTime.now().minusMinutes(30)));

        assertThatThrownBy(() -> service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.challenge.resendTooSoon");
                assertThat((Long) exception.getArgs().values().get("retryAfterSeconds")).isBetween(1700L, 1801L);
            });
    }

    @Test
    void recordsTheSignaturesWithTheRightCode() {
        Challenge challenge = service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1"));
        ActOutcome outcome = new ActOutcome(30L, SignatureActType.ASSIGNMENT_ACKNOWLEDGMENT, SignatureActStatus.SIGNED, 5L);
        when(handler.confirm(any(SignatureChallenge.class), any(Signer.class))).thenReturn(List.of(outcome));

        Confirmation confirmation = service.confirm(challenge.id(), new ConfirmRequest(sentCode()), http(), token("user-1"));

        assertThat(confirmation.acts()).containsExactly(outcome);
        SignatureChallenge stored = store.get(challenge.id());
        assertThat(stored.getStatus()).isEqualTo(SignatureChallengeStatus.CONFIRMED);
        assertThat(stored.getConfirmedAt()).isNotNull();
        assertThat(stored.getCodeDigest()).isNull();
        ArgumentCaptor<Signer> signer = ArgumentCaptor.forClass(Signer.class);
        verify(handler).confirm(eq(stored), signer.capture());
        assertThat(signer.getValue().userId()).isEqualTo("user-1");
        assertThat(signer.getValue().displayName()).isEqualTo("Mario Rossi");
        assertThat(signer.getValue().sessionId()).isEqualTo("session-1");
        assertThat(signer.getValue().ipAddress()).isEqualTo("192.0.2.10");
        assertThat(signer.getValue().userAgent()).isEqualTo("Mozilla/5.0");

        assertError(() -> service.confirm(challenge.id(), new ConfirmRequest(sentCode()), http(), token("user-1")), "signature.challenge.closed");
        verify(handler, times(1)).confirm(any(), any());
    }

    @Test
    void consumesAnAttemptForEveryWrongCodeAndLocksAfterTheLastOne() {
        Challenge challenge = service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1"));
        String wrong = sentCode().equals("000000") ? "000001" : "000000";

        for (int remaining = 4; remaining >= 1; remaining--) {
            int expected = remaining;
            assertThatThrownBy(() -> service.confirm(challenge.id(), new ConfirmRequest(wrong), http(), token("user-1")))
                .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                    assertThat(exception.getErrorKey()).isEqualTo("signature.challenge.invalidCode");
                    assertThat(exception.getArgs().values()).containsEntry("remainingAttempts", expected);
                });
        }
        assertError(() -> service.confirm(challenge.id(), new ConfirmRequest(wrong), http(), token("user-1")), "signature.challenge.locked");
        assertError(() -> service.confirm(challenge.id(), new ConfirmRequest(sentCode()), http(), token("user-1")), "signature.challenge.locked");
        assertThat(store.get(challenge.id()).getStatus()).isEqualTo(SignatureChallengeStatus.LOCKED);
        verify(handler, never()).confirm(any(), any());
    }

    @Test
    void refusesAnExpiredCode() {
        Challenge challenge = service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1"));
        store.get(challenge.id()).setExpiresAt(ZonedDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> service.confirm(challenge.id(), new ConfirmRequest(sentCode()), http(), token("user-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.challenge.expired");
                assertThat(exception.getStatusCode().value()).isEqualTo(410);
            });
        assertThat(store.get(challenge.id()).getStatus()).isEqualTo(SignatureChallengeStatus.EXPIRED);
    }

    @Test
    void closesTheChallengeWhenTheItemsAreNoLongerValid() {
        Challenge challenge = service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1"));
        when(handler.confirm(any(), any())).thenThrow(
            new RequestAlertException(HttpStatus.CONFLICT, "changed", "inventory", "inventory.revision.versionConflict")
        );

        assertError(() -> service.confirm(challenge.id(), new ConfirmRequest(sentCode()), http(), token("user-1")), "inventory.revision.versionConflict");
        assertThat(store.get(challenge.id()).getStatus()).isEqualTo(SignatureChallengeStatus.CANCELLED);
    }

    @Test
    void hidesTheChallengesOfOtherUsers() {
        Challenge challenge = service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1"));

        assertError(() -> service.confirm(challenge.id(), new ConfirmRequest(sentCode()), http(), token("user-2")), "signature.challenge.notFound");
        assertError(() -> service.resend(challenge.id(), token("user-2")), "signature.challenge.notFound");
    }

    @Test
    void replacesTheCodeOnlyAfterTheIntervalAndUpToTheLimit() {
        Challenge challenge = service.create(request(SignatureMethod.EMAIL_OTP), http(), token("user-1"));
        String first = sentCode();

        assertThatThrownBy(() -> service.resend(challenge.id(), token("user-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.challenge.resendTooSoon");
                assertThat((Long) exception.getArgs().values().get("retryAfterSeconds")).isBetween(1L, 61L);
            });

        for (int sends = 2; sends <= 5; sends++) {
            store.get(challenge.id()).setLastSentAt(ZonedDateTime.now().minusMinutes(2));
            Challenge resent = service.resend(challenge.id(), token("user-1"));
            assertThat(resent.remainingSends()).isEqualTo(5 - sends);
        }
        String last = sentCode();
        store.get(challenge.id()).setLastSentAt(ZonedDateTime.now().minusMinutes(2));
        assertError(() -> service.resend(challenge.id(), token("user-1")), "signature.challenge.limit");

        if (!first.equals(last)) {
            assertError(() -> service.confirm(challenge.id(), new ConfirmRequest(first), http(), token("user-1")), "signature.challenge.invalidCode");
        }
        when(handler.confirm(any(), any())).thenReturn(List.of());
        assertThat(service.confirm(challenge.id(), new ConfirmRequest(last), http(), token("user-1")).challengeId()).isEqualTo(challenge.id());
    }

    @Test
    void describesTheMethodsOfTheUser() {
        when(inventoryService.electronicSignatureEnabled()).thenReturn(true);
        when(otpClient.status("user-1")).thenReturn(KeycloakOtpClient.Status.NOT_CONFIGURED);

        assertThat(service.methods(token("user-1"))).isEqualTo(new Methods(true, SignatureMethod.EMAIL_OTP, SignatureMethod.EMAIL_OTP, true, false, "m***@example.org"));

        when(preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc("user-1", SignatureChallengeService.METHOD_PREFERENCE))
            .thenReturn(Optional.of(new Preferences().key(SignatureChallengeService.METHOD_PREFERENCE).value("TOTP")));
        assertThat(service.methods(token("user-1"))).isEqualTo(new Methods(true, SignatureMethod.TOTP, SignatureMethod.EMAIL_OTP, true, false, "m***@example.org"));

        when(otpClient.status("user-1")).thenReturn(KeycloakOtpClient.Status.CONFIGURED);
        assertThat(service.methods(token("user-1"))).isEqualTo(new Methods(true, SignatureMethod.TOTP, SignatureMethod.TOTP, true, true, "m***@example.org"));

        when(otpClient.status("user-1")).thenReturn(KeycloakOtpClient.Status.UNAVAILABLE);
        assertThat(service.methods(token("user-1"))).isEqualTo(new Methods(true, SignatureMethod.TOTP, SignatureMethod.EMAIL_OTP, false, false, "m***@example.org"));
    }

    @Test
    void asksKeycloakOnlyWhenTheTenantUsesTheSignature() {
        when(inventoryService.electronicSignatureEnabled()).thenReturn(false);

        assertThat(service.methods(token("user-1")).enabled()).isFalse();

        verify(otpClient, never()).status(anyString());
    }

    @Test
    void savesThePreferredMethod() {
        when(otpClient.status("user-1")).thenReturn(KeycloakOtpClient.Status.NOT_CONFIGURED);
        when(preferencesRepository.save(any(Preferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Methods methods = service.updatePreference(SignatureMethod.TOTP, token("user-1"));

        ArgumentCaptor<Preferences> saved = ArgumentCaptor.forClass(Preferences.class);
        verify(preferencesRepository).save(saved.capture());
        assertThat(saved.getValue().getKey()).isEqualTo("signature.method");
        assertThat(saved.getValue().getValue()).isEqualTo("TOTP");
        assertThat(saved.getValue().getUserId()).isEqualTo("user-1");
        assertThat(saved.getValue().getDeleted()).isFalse();
        assertThat(methods.proposed()).isEqualTo(SignatureMethod.EMAIL_OTP);

        Preferences existing = new Preferences().id(9L).key(SignatureChallengeService.METHOD_PREFERENCE).value("TOTP").userId("user-1");
        when(preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc("user-1", SignatureChallengeService.METHOD_PREFERENCE)).thenReturn(Optional.of(existing));
        service.updatePreference(SignatureMethod.EMAIL_OTP, token("user-1"));

        assertThat(existing.getValue()).isEqualTo("EMAIL");
        verify(preferencesRepository).save(existing);
    }

    @Test
    void sendsTheCodeOfALinkToTheAddressOfTheParent() {
        Challenge challenge = service.createForLink(session(40L), new LinkChallengeRequest(SignaturePurpose.ACCEPTANCE, "c".repeat(64)), http());

        SignatureChallenge stored = store.get(challenge.id());
        assertThat(stored.getLinkId()).isEqualTo(40L);
        assertThat(stored.getUserKeycloakId()).isNull();
        assertThat(stored.getDestinationEmail()).isEqualTo("genitore@example.org");
        assertThat(stored.getInsertBy()).isEqualTo("signature-link-40");
        assertThat(challenge.maskedEmail()).isEqualTo("g***@example.org");
        ArgumentCaptor<List<ChallengeItem>> items = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Signer> signer = ArgumentCaptor.forClass(Signer.class);
        verify(acceptanceHandler).prepare(items.capture(), signer.capture());
        assertThat(items.getValue()).containsExactly(ChallengeItem.document(10L, SignatureDocumentRole.MAIN, "c".repeat(64)));
        assertThat(signer.getValue().userId()).isNull();
        assertThat(signer.getValue().signerId()).isEqualTo(30L);
        assertThat(signer.getValue().sessionId()).isEqualTo("link:abcd1234");
        assertThat(signer.getValue().token()).isNull();
        verify(linkService, atLeastOnce()).requireActive(session(40L));
    }

    @Test
    void confirmsTheCodeOfALinkOnlyThroughThatLink() {
        Challenge challenge = service.createForLink(session(40L), new LinkChallengeRequest(SignaturePurpose.ACCEPTANCE, "c".repeat(64)), http());
        String code = sentCode();
        when(acceptanceHandler.confirm(any(), any())).thenReturn(List.of(new ActOutcome(10L, SignatureActType.LOAN_SHEET_ISSUE, SignatureActStatus.AWAITING_SIGNATURES, null)));

        assertError(() -> service.confirmForLink(session(41L), challenge.id(), new ConfirmRequest(code), http()), "signature.challenge.notFound");
        assertError(() -> service.confirm(challenge.id(), new ConfirmRequest(code), http(), token("user-1")), "signature.challenge.notFound");

        Confirmation confirmation = service.confirmForLink(session(40L), challenge.id(), new ConfirmRequest(code), http());

        assertThat(confirmation.acts()).hasSize(1);
        assertThat(store.get(challenge.id()).getStatus()).isEqualTo(SignatureChallengeStatus.CONFIRMED);
    }

    @Test
    void refusesThroughALinkWhatOnlyAnAccountCanSign() {
        assertError(
            () -> service.createForLink(session(40L), new LinkChallengeRequest(SignaturePurpose.ASSOCIATION_SIGNATURE, "c".repeat(64)), http()),
            "signature.challenge.invalid"
        );
        doThrow(new SignatureLinkUnavailableException()).when(linkService).requireActive(any());
        assertThatThrownBy(() -> service.createForLink(session(40L), new LinkChallengeRequest(SignaturePurpose.ACCEPTANCE, "c".repeat(64)), http()))
            .isInstanceOf(SignatureLinkUnavailableException.class);
        verify(mailService, never()).sendCode(any());
    }

    private static SignatureLinkSession session(long linkId) {
        return new SignatureLinkSession("tenant-a", linkId, 30L, 10L, "abcd1234", ZonedDateTime.parse("2026-10-26T10:00:00+02:00"));
    }

    private void configureApp() {
        when(otpClient.configured()).thenReturn(true);
        when(otpClient.status("user-1")).thenReturn(KeycloakOtpClient.Status.CONFIGURED);
    }

    private SignatureChallenge store(SignatureChallenge challenge) {
        if (challenge.getId() == null) challenge.setId((long) store.size() + 1);
        store.put(challenge.getId(), challenge);
        return challenge;
    }

    private String sentCode() {
        ArgumentCaptor<SignatureMailService.CodeMessage> captor = ArgumentCaptor.forClass(SignatureMailService.CodeMessage.class);
        verify(mailService, atLeastOnce()).sendCode(captor.capture());
        return captor.getValue().code();
    }

    private static ChallengeRequest request(SignatureMethod method) {
        return new ChallengeRequest(SignaturePurpose.ASSIGNMENT_DECISION, method, ITEMS);
    }

    private static MockHttpServletRequest http() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "Mozilla/5.0");
        return request;
    }

    private static JwtAuthenticationToken token(String userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(userId).claim("tenant", "tenant-a").claim("sid", "session-1")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String key) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo(key));
    }
}
