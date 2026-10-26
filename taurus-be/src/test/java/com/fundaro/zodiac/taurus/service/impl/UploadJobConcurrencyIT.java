package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qualifica il comportamento di più worker sullo stesso job di upload, come avviene con più
 * repliche dell'applicazione: la presa in carico è un UPDATE condizionato sullo stato, quindi un
 * solo worker passa il job a `IN_PROGRESS` e gli altri si ritirano.
 *
 * <p>Il runbook di test chiede di qualificare gli scheduler multi-replica: questa è la prova del
 * meccanismo usato dalla coda degli upload. I promemoria push e l'outbox delle notifiche non hanno
 * ancora una presa in carico equivalente, e il gap lo dice.
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
class UploadJobConcurrencyIT {

    private static final int WORKERS = 6;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final String tenant = "upload-concurrency-" + UUID.randomUUID();

    @Autowired
    private TenantSchemaProvisioningService provisioningService;

    @Autowired
    private TenantTransactionExecutor transactionExecutor;

    @Autowired
    private QueueUploadFilesRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void provisionTenant() {
        provisioningService.provision(tenant);
    }

    @AfterEach
    void dropTenant() {
        provisioningService.dropSchema(tenant);
    }

    @Test
    void letsOnlyOneWorkerTakeTheSameJob() throws Exception {
        Long jobId = createJob(UploadFileStatusEnum.TO_PROCESS);

        List<Boolean> outcomes = inParallel(() ->
            transactionExecutor.execute(tenant, () ->
                repository.transitionStatus(jobId, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, "worker-test") == 1
            )
        );

        assertThat(outcomes).containsOnlyOnce(true);
        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        transactionExecutor.execute(tenant, () -> {
            assertThat(repository.findById(jobId).orElseThrow().getStatus()).isEqualTo(UploadFileStatusEnum.IN_PROGRESS);
        });
    }

    @Test
    void letsOnlyOneWorkerCloseTheSameJob() throws Exception {
        Long jobId = createJob(UploadFileStatusEnum.IN_PROGRESS);

        List<Boolean> outcomes = inParallel(() ->
            transactionExecutor.execute(tenant, () ->
                repository.transitionStatus(jobId, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.DONE, "worker-test") == 1
            )
        );

        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        transactionExecutor.execute(tenant, () -> {
            assertThat(repository.findById(jobId).orElseThrow().getStatus()).isEqualTo(UploadFileStatusEnum.DONE);
        });
    }

    /**
     * Un job con le sue righe obbligatorie: utente e media esistono per vincolo, quindi la riga si
     * costruisce in SQL invece di passare dai servizi, che chiederebbero un upload vero.
     */
    private Long createJob(UploadFileStatusEnum status) {
        return transactionExecutor.execute(tenant, () -> {
            String subject = "worker-test-" + UUID.randomUUID();
            Long identityId = jdbcTemplate.queryForObject(
                "insert into public.user_identity (keycloak_id) values (?) returning id", Long.class, subject);
            Long userId = jdbcTemplate.queryForObject(
                "insert into app_user (user_identity_id, keycloak_id, name) values (?, ?, ?) returning id",
                Long.class, identityId, subject, "Worker di prova");
            Long mediaId = jdbcTemplate.queryForObject(
                "insert into media_asset (name, storage_key, mime_type, original_filename, file_extension, file_size, sha256, status)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, "score.pdf", "tenant/score.pdf", "application/pdf", "score.pdf", "pdf", 1024L,
                "0".repeat(64), "READY");

            return jdbcTemplate.queryForObject(
                "insert into upload_job (user_id, source_media_asset_id, name, status, type) values (?, ?, ?, ?, ?) returning id",
                Long.class, userId, mediaId, "score.pdf", status.name(), "Tracks");
        });
    }

    /** Esegue l'azione da più thread che partono insieme, come repliche diverse dell'applicazione. */
    private List<Boolean> inParallel(Callable<Boolean> action) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new java.util.ArrayList<>();
            for (int worker = 0; worker < WORKERS; worker++) {
                futures.add(workers.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();

            List<Boolean> outcomes = new java.util.ArrayList<>();
            for (Future<Boolean> future : futures) outcomes.add(future.get(30, TimeUnit.SECONDS));

            return outcomes;
        } finally {
            workers.shutdownNow();
        }
    }
}
