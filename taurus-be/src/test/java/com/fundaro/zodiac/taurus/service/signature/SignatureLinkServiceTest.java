package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLink;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLinkRegistry;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChallengeRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRegistryRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignatureLinkServiceTest {

    private static final SignatureLinkService.LinkContent CONTENT = new SignatureLinkService.LinkContent("2026/007", "Emissione del foglio", "Luca Rossi");

    @Mock
    SignatureLinkRepository linkRepository;
    @Mock
    SignatureLinkRegistryRepository registryRepository;
    @Mock
    SignatureActSignerRepository signerRepository;
    @Mock
    SignatureActRepository actRepository;
    @Mock
    SignatureChallengeRepository challengeRepository;
    @Mock
    TenantsRepository tenantsRepository;
    @Mock
    SignatureMailService mailService;
    @Mock
    TenantTimeZoneService tenantTimeZoneService;
    @Mock
    TenantTransactionExecutor tenantTransactionExecutor;

    private final Map<Long, SignatureLink> links = new HashMap<>();
    private final SignatureLinkTokens tokens = new SignatureLinkTokens();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private SignatureLinkService service;
    private SignatureActSigner parent;

    @BeforeEach
    void setUp() {
        Tenants tenant = new Tenants();
        tenant.setId(4L);
        tenant.setCode("tenant-a");
        tenant.setName("Banda musicale di prova");
        when(tenantsRepository.findByCodeAndDeletedFalse("tenant-a")).thenReturn(Optional.of(tenant));
        when(tenantTimeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        when(linkRepository.saveAndFlush(any(SignatureLink.class))).thenAnswer(invocation -> store(invocation.getArgument(0)));
        when(linkRepository.save(any(SignatureLink.class))).thenAnswer(invocation -> store(invocation.getArgument(0)));
        when(linkRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(links.get(invocation.<Long>getArgument(0))));
        when(linkRepository.findActiveBySigner(anyLong(), any())).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTransactionExecutor).execute(anyString(), any(Runnable.class));
        when(tenantTransactionExecutor.execute(any(), any(Supplier.class))).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());

        parent = new SignatureActSigner();
        parent.setId(30L);
        parent.setActId(10L);
        parent.setSignerRole(SignerRole.HOLDER);
        parent.setDisplayName("Giulia Rossi");
        parent.setEmail("genitore@example.org");

        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().setPublicBaseUrl("https://app.example.org/");
        service = new SignatureLinkService(
            linkRepository,
            registryRepository,
            signerRepository,
            actRepository,
            challengeRepository,
            tenantsRepository,
            tokens,
            mailService,
            tenantTimeZoneService,
            tenantTransactionExecutor,
            new SignatureMetrics(registry),
            properties
        );
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void createsALinkFromTheConfiguredAddressAndRevokesThePreviousOne() {
        SignatureLink previous = link(5L, ZonedDateTime.now().plusDays(2));
        when(linkRepository.findActiveBySigner(eq(30L), any())).thenReturn(List.of(previous));

        SignatureLinkService.PendingLink pending = TenantContext.call("tenant-a", () -> service.create(parent, CONTENT, false, "admin-1"));

        assertThat(previous.getRevokedAt()).isNotNull();
        verify(registryRepository).revoke(eq(4L), eq(5L), any());
        String token = pending.url().substring("https://app.example.org/firma/".length());
        assertThat(pending.url()).startsWith("https://app.example.org/firma/");
        ArgumentCaptor<SignatureLinkRegistry> route = ArgumentCaptor.forClass(SignatureLinkRegistry.class);
        verify(registryRepository).save(route.capture());
        assertThat(route.getValue().getTokenDigest()).isEqualTo(tokens.digest(token));
        assertThat(route.getValue().getTenantId()).isEqualTo(4L);
        SignatureLink created = links.get(pending.linkId());
        assertThat(route.getValue().getLinkId()).isEqualTo(created.getId());
        assertThat(created.getSignerId()).isEqualTo(30L);
        assertThat(created.getTokenFingerprint()).isEqualTo(tokens.digest(token).substring(0, 8));
        assertThat(created.getExpiresAt()).isCloseTo(ZonedDateTime.now().plusHours(7 * 24), within(1, ChronoUnit.MINUTES));
        assertThat(created.getSentAt()).isNull();
        assertThat(pending.destination()).isEqualTo("genitore@example.org");
        assertThat(pending.tenantName()).isEqualTo("Banda musicale di prova");
        assertThat(pending.toString()).doesNotContain(token);
    }

    @Test
    void sendsALinkOnlyToASignerWithoutAnAccount() {
        parent.setUserKeycloakId("member-1");

        assertThatThrownBy(() -> TenantContext.call("tenant-a", () -> service.create(parent, CONTENT, false, "admin-1"))).isInstanceOf(IllegalStateException.class);
        verify(registryRepository, never()).save(any());
    }

    @Test
    void sendsTheLinkAfterTheCommitAndRecordsWhenTheServerAcceptedIt() {
        TransactionSynchronizationManager.initSynchronization();
        TenantContext.run("tenant-a", () -> service.createAndSendAfterCommit(parent, CONTENT, false, "admin-1"));
        verify(mailService, never()).sendLink(any());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<SignatureMailService.LinkMessage> message = ArgumentCaptor.forClass(SignatureMailService.LinkMessage.class);
        verify(mailService).sendLink(message.capture());
        assertThat(message.getValue().destination()).isEqualTo("genitore@example.org");
        assertThat(message.getValue().minorName()).isEqualTo("Luca Rossi");
        assertThat(message.getValue().signed()).isFalse();
        assertThat(links.values()).singleElement().satisfies(link -> assertThat(link.getSentAt()).isNotNull());
    }

    @Test
    void keepsALinkUnsentWhenTheServerRefusesIt() {
        doThrow(new MailSendException("refused")).when(mailService).sendLink(any());
        SignatureLinkService.PendingLink pending = TenantContext.call("tenant-a", () -> service.create(parent, CONTENT, true, "admin-1"));

        assertThat(service.deliver(pending)).isFalse();
        assertThat(links.get(pending.linkId()).getSentAt()).isNull();
        assertThat(registry.counter("taurus.signature.links", "outcome", "mailFailed").count()).isEqualTo(1);
    }

    @Test
    void opensOnlyAValidLinkOfAParentOnAnActStillInTaurus() {
        String digest = "abcd1234" + "0".repeat(56);
        SignatureLink link = link(5L, ZonedDateTime.now().plusDays(2));
        link.setTokenFingerprint("abcd1234");
        SignatureAct act = new SignatureAct();
        act.setId(10L);
        act.setActType(SignatureActType.LOAN_SHEET_ISSUE);
        act.setStatus(SignatureActStatus.AWAITING_DATA);
        when(signerRepository.findById(30L)).thenReturn(Optional.of(parent));
        when(actRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(act));
        SignatureLinkRegistry route = new SignatureLinkRegistry();
        route.setLinkId(5L);
        ZonedDateTime now = ZonedDateTime.now();

        SignatureLinkSession session = service.open(route, digest, "tenant-a", now);

        assertThat(session).isEqualTo(new SignatureLinkSession("tenant-a", 5L, 30L, 10L, "abcd1234", link.getExpiresAt()));
        assertThat(link.getLastUsedAt()).isEqualTo(now);
        assertThat(session.actor()).isEqualTo("signature-link-5");

        assertThatThrownBy(() -> service.open(route, "ffff0000" + "0".repeat(56), "tenant-a", now)).isInstanceOf(SignatureLinkUnavailableException.class);
        act.setStatus(SignatureActStatus.CANCELLED);
        assertThatThrownBy(() -> service.open(route, digest, "tenant-a", now)).isInstanceOf(SignatureLinkUnavailableException.class);
        act.setStatus(SignatureActStatus.SIGNED);
        parent.setUserKeycloakId("member-1");
        assertThatThrownBy(() -> service.open(route, digest, "tenant-a", now)).isInstanceOf(SignatureLinkUnavailableException.class);
        parent.setUserKeycloakId(null);
        link.setRevokedAt(now.minusMinutes(1));
        assertThatThrownBy(() -> service.open(route, digest, "tenant-a", now)).isInstanceOf(SignatureLinkUnavailableException.class);
        link.setRevokedAt(null);
        link.setExpiresAt(now.minusSeconds(1));
        assertThatThrownBy(() -> service.open(route, digest, "tenant-a", now)).isInstanceOf(SignatureLinkUnavailableException.class);
    }

    @Test
    void showsTheLastLinkOfEverySignerWithoutAnAccount() {
        SignatureLink expired = link(5L, ZonedDateTime.now().minusDays(1));
        SignatureLink active = link(6L, ZonedDateTime.now().plusDays(6));
        active.setSentAt(ZonedDateTime.now());
        when(linkRepository.findAllBySignerIdInAndDeletedFalseOrderByIdAsc(List.of(30L))).thenReturn(List.of(expired, active));
        SignatureActSigner admin = new SignatureActSigner();
        admin.setId(31L);
        admin.setUserKeycloakId("admin-1");

        var views = service.views(List.of(parent, admin));

        assertThat(views).containsOnlyKeys(30L);
        assertThat(views.get(30L).active()).isTrue();
        assertThat(views.get(30L).sentAt()).isEqualTo(active.getSentAt());
    }

    private SignatureLink link(long id, ZonedDateTime expiresAt) {
        SignatureLink link = new SignatureLink();
        link.setId(id);
        link.setSignerId(30L);
        link.setTokenFingerprint("00000000");
        link.setExpiresAt(expiresAt);
        links.put(id, link);
        return link;
    }

    private SignatureLink store(SignatureLink link) {
        if (link.getId() == null) link.setId(100L + links.size());
        links.put(link.getId(), link);
        return link;
    }
}
