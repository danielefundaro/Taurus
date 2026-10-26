package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceFrequency;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.CalendarEventSeriesRepository;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesRequest;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceEndDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceRuleDTO;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qualifica la serie ricorrente sui fronti chiesti dalla specifica: isolamento fra tenant,
 * idempotenza della creazione e comportamento di due richieste concorrenti sulla stessa serie.
 *
 * <p>Le autorizzazioni dei percorsi stanno in {@code CalendarSeriesSecurityConfigurationTest}, che
 * prova le regole vere della configurazione di sicurezza senza bisogno di un database.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class CalendarEventSeriesIT {

    private static final int WORKERS = 6;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final String ownerTenant = "series-owner-" + UUID.randomUUID();
    private final String intruderTenant = "series-intruder-" + UUID.randomUUID();

    @Autowired
    private TenantSchemaProvisioningService provisioningService;

    @Autowired
    private TenantTransactionExecutor transactionExecutor;

    @Autowired
    private CalendarEventSeriesService seriesService;

    @Autowired
    private CalendarEventSeriesRepository seriesRepository;

    @Autowired
    private CalendarEventsRepository eventRepository;

    @Autowired
    private TenantsRepository tenantsRegistry;

    @BeforeAll
    static void requireDocker() {
        DockerRequirement.required();
    }

    @BeforeEach
    void provisionBothTenants() {
        // I servizi del calendario pretendono una riga tenant attiva, non solo lo schema.
        registerTenant(ownerTenant);
        registerTenant(intruderTenant);
        provisioningService.provision(ownerTenant);
        provisioningService.provision(intruderTenant);
    }

    @AfterEach
    void dropBothTenants() {
        try {
            provisioningService.dropSchema(ownerTenant);
            provisioningService.dropSchema(intruderTenant);
        } finally {
            unregisterTenant(ownerTenant);
            unregisterTenant(intruderTenant);
        }
    }

    private void registerTenant(String code) {
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setFinanceEnabled(true);
        tenant.setDeleted(false);
        tenant.setInsertBy("user-1");
        tenant.setInsertDate(now);
        tenant.setEditBy("user-1");
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRegistry.save(tenant);
    }

    private void unregisterTenant(String code) {
        tenantsRegistry.findByCodeAndDeletedFalse(code).ifPresent(tenantsRegistry::delete);
    }

    @Test
    void keepsTheSeriesAndItsOccurrencesInsideItsTenant() {
        CalendarEventSeriesDTO created = create(ownerTenant, request(null));
        assertThat(created.getCreatedCount()).isEqualTo(4);

        // L'errore va provato fuori dalla transazione: catturarlo dentro la marcherebbe rollback-only.
        assertThatThrownBy(() -> transactionExecutor.execute(intruderTenant, () -> seriesService.findOne(created.getId())))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("not found");

        transactionExecutor.execute(intruderTenant, () -> {
            assertThat(seriesRepository.count()).isZero();
            assertThat(eventRepository.count()).isZero();
        });

        transactionExecutor.execute(ownerTenant, () -> {
            assertThat(seriesService.findOne(created.getId()).getOccurrenceCount()).isEqualTo(4);
        });
    }

    @Test
    void doesNotTouchTheSeriesOfAnotherTenantFromItsOwnSchema() {
        CalendarEventSeriesDTO created = create(ownerTenant, request(null));
        Long occurrenceId = transactionExecutor.execute(ownerTenant, () ->
            eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(created.getId()).get(0).getId()
        );

        assertThatThrownBy(() ->
                transactionExecutor.execute(
                    intruderTenant,
                    () -> seriesService.restoreOccurrence(created.getId(), occurrenceId, authentication(intruderTenant))
                )
            )
            .isInstanceOf(RequestAlertException.class);

        transactionExecutor.execute(ownerTenant, () -> {
            assertThat(seriesService.findOne(created.getId()).getOccurrenceCount()).isEqualTo(4);
        });
    }

    @Test
    void returnsTheSameSeriesWhenTheCreationIsRepeatedWithTheSameKey() {
        String key = "idem-" + UUID.randomUUID();
        CalendarEventSeriesDTO first = create(ownerTenant, request(key));
        CalendarEventSeriesDTO second = create(ownerTenant, request(key));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getReplayed()).isTrue();
        assertThat(second.getCreatedCount()).isEqualTo(first.getCreatedCount());
        transactionExecutor.execute(ownerTenant, () -> {
            assertThat(seriesRepository.count()).isEqualTo(1);
            assertThat(eventRepository.count()).isEqualTo(4);
        });
    }

    @Test
    void createsOneSeriesOnlyWhenTheSameKeyArrivesFromSeveralRequestsAtOnce() throws Exception {
        String key = "idem-" + UUID.randomUUID();

        List<String> outcomes = inParallel(() -> {
            try {
                return "created:" + create(ownerTenant, request(key)).getId();
            } catch (RequestAlertException exception) {
                return "refused:" + exception.getErrorKey();
            }
        });

        assertThat(outcomes).anyMatch(outcome -> outcome.startsWith("created:"));
        // Chi perde la corsa riceve un conflitto riconoscibile, non una seconda serie.
        assertThat(outcomes)
            .filteredOn(outcome -> outcome.startsWith("refused:"))
            .allMatch(outcome -> outcome.endsWith("duplicateRequest"));
        transactionExecutor.execute(ownerTenant, () -> {
            assertThat(seriesRepository.count()).isEqualTo(1);
            assertThat(eventRepository.count()).isEqualTo(4);
        });
    }

    @Test
    void letsOnlyOneConcurrentChangeOfTheSameSeriesThrough() throws Exception {
        CalendarEventSeriesDTO created = create(ownerTenant, request(null));

        List<String> outcomes = inParallel(() -> {
            try {
                CalendarEventSeriesRequest own = request(null);
                own.getRecurrence().getEnd().setCount(6);
                own.setEntityVersion(created.getEntityVersion());
                CalendarEventSeriesDTO updated = transactionExecutor.execute(
                    ownerTenant,
                    () -> seriesService.update(created.getId(), own, authentication(ownerTenant))
                );
                return "updated:" + updated.getCreatedCount();
            } catch (RequestAlertException exception) {
                return "refused:" + exception.getErrorKey();
            } catch (RuntimeException exception) {
                return "refused:concurrency";
            }
        });

        // Una sola richiesta allunga la serie: le altre vedono la versione già cambiata.
        assertThat(outcomes.stream().filter(outcome -> outcome.startsWith("updated:")).count()).isEqualTo(1);
        transactionExecutor.execute(ownerTenant, () -> {
            assertThat(seriesService.findOne(created.getId()).getOccurrenceCount()).isEqualTo(6);
            assertThat(seriesRepository.count()).isEqualTo(1);
        });
    }

    private CalendarEventSeriesDTO create(String tenant, CalendarEventSeriesRequest request) {
        return transactionExecutor.execute(tenant, () -> seriesService.create(request, authentication(tenant)));
    }

    /** Una serie giornaliera di quattro occorrenze future, con la chiave di idempotenza indicata. */
    private CalendarEventSeriesRequest request(String idempotencyKey) {
        CalendarEventsDTO template = new CalendarEventsDTO();
        template.setName("Prova di serie");
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        template.setStartDate(Date.from(start));
        template.setEndDate(Date.from(start.plus(90, ChronoUnit.MINUTES)));

        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(RecurrenceEndType.COUNT);
        end.setCount(4);

        RecurrenceRuleDTO rule = new RecurrenceRuleDTO();
        rule.setFrequency(RecurrenceFrequency.DAILY);
        rule.setInterval(1);
        rule.setEnd(end);

        CalendarEventSeriesRequest request = new CalendarEventSeriesRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setTemplate(template);
        request.setRecurrence(rule);
        return request;
    }

    private JwtAuthenticationToken authentication(String tenantCode) {
        Instant now = Instant.now();
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", tenantCode)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    /** Esegue l'azione da più thread che partono insieme, come repliche diverse dell'applicazione. */
    private List<String> inParallel(Callable<String> action) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int worker = 0; worker < WORKERS; worker++) {
                futures.add(workers.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) outcomes.add(future.get(60, TimeUnit.SECONDS));

            return outcomes;
        } finally {
            workers.shutdownNow();
        }
    }
}
