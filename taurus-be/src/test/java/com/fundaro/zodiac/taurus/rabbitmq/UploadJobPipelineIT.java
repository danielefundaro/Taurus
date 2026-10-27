package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.upload.UploadJobRecovery;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il percorso completo di un caricamento su PostgreSQL e RabbitMQ reali: la richiesta accoda il
 * job, il broker consegna il messaggio, il worker converte il PDF, crea le parti e porta il job a
 * {@code DONE}. Fino a questa suite la coda era sempre finta, quindi la catena intera — commit,
 * pubblicazione, consumo, transizioni — non era mai stata osservata tutta insieme.
 *
 * <p>Qui vivono anche le prove che hanno senso solo con un broker vero: il consumatore fermato e
 * riavviato con un job in coda, il nuovo tentativo di un job in errore e il recupero di un job
 * rimasto in elaborazione. L'isolamento fra tenant è verificato sulle stesse righe reali.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        // Il recupero automatico non deve partire da sé in mezzo alle prove.
        "application.upload.recovery-enabled=false",
    }
)
class UploadJobPipelineIT {

    private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:3.13-management-alpine").withReuse(true);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static Path storageRoot;

    static {
        BROKER.start();
    }

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) throws IOException {
        storageRoot = Files.createTempDirectory("upload-pipeline");
        registry.add("application.base-path", () -> storageRoot.toString());
        registry.add("spring.rabbitmq.host", BROKER::getHost);
        registry.add("spring.rabbitmq.port", BROKER::getAmqpPort);
        registry.add("spring.rabbitmq.username", BROKER::getAdminUsername);
        registry.add("spring.rabbitmq.password", BROKER::getAdminPassword);
    }

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    TenantsRepository tenantsRegistry;
    @Autowired
    TracksService tracksService;
    @Autowired
    QueueUploadFilesRepository jobRepository;
    @Autowired
    UploadJobRecovery recovery;
    @Autowired
    SimpleMessageListenerContainer listenerContainer;
    @Autowired
    RabbitListenerEndpointRegistry listenerRegistry;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final String tenant = "upload-pipeline-" + UUID.randomUUID();
    private final String otherTenant = "upload-other-" + UUID.randomUUID();
    /** Il soggetto del token: esiste come utente in ciascuno dei due tenant. */
    private final String subject = "upload-user-" + UUID.randomUUID();

    @BeforeAll
    static void requireDocker() {
        DockerRequirement.required();
    }

    @BeforeEach
    void provisionTenants() {
        registerTenant(tenant);
        registerTenant(otherTenant);
        provisioningService.provision(tenant);
        provisioningService.provision(otherTenant);
        Long identityId = jdbcTemplate.queryForObject(
            "insert into public.user_identity (keycloak_id) values (?) returning id", Long.class, subject);
        createUser(tenant, identityId);
        createUser(otherTenant, identityId);
    }

    /** L'utente che carica il file: il servizio rifiuta un token senza un utente nel tenant. */
    private void createUser(String tenantCode, Long identityId) {
        transactionExecutor.execute(tenantCode, () ->
            jdbcTemplate.update(
                "insert into app_user (user_identity_id, keycloak_id, name) values (?, ?, ?)",
                identityId, subject, "Utente di prova"
            )
        );
    }

    @AfterEach
    void dropTenants() {
        try {
            startConsumers();
            provisioningService.dropSchema(tenant);
            provisioningService.dropSchema(otherTenant);
        } finally {
            unregisterTenant(tenant);
            unregisterTenant(otherTenant);
        }
    }

    @Test
    void carriesAnUploadFromTheRequestToTheCreatedParts() throws Exception {
        Long jobId = upload("partitura.pdf");

        QueueUploadFiles completed = await(() -> terminalJob(jobId));

        assertThat(completed.getStatus()).isEqualTo(UploadFileStatusEnum.DONE);
        assertThat(transactionExecutor.execute(tenant, () -> tracksService.findOne(completed.getTrackId(), authentication(tenant))))
            .isPresent();
    }

    /**
     * Il consumatore fermo non perde il lavoro: il messaggio resta nel broker e il job resta in
     * coda, poi il riavvio lo porta a termine. È il caso dell'applicazione riavviata durante un
     * caricamento.
     */
    @Test
    void keepsTheJobQueuedWhileTheConsumerIsStoppedAndFinishesItAfterTheRestart() throws Exception {
        stopConsumers();
        Long jobId = upload("partitura.pdf");

        Thread.sleep(2_000);
        assertThat(job(jobId).getStatus()).isEqualTo(UploadFileStatusEnum.TO_PROCESS);

        startConsumers();

        assertThat(await(() -> terminalJob(jobId)).getStatus()).isEqualTo(UploadFileStatusEnum.DONE);
    }

    /**
     * La coda {@code upload.files} ha due consumatori nel contesto: quello dichiarato in
     * {@code RabbitMQConfig} e quello generato dall'annotazione sul {@link Receiver}. Per fermare
     * il consumo vanno fermati entrambi, altrimenti il messaggio viene preso dall'altro.
     */
    private void stopConsumers() {
        listenerContainer.stop();
        listenerRegistry.getListenerContainers().forEach(container -> container.stop());
    }

    private void startConsumers() {
        if (!listenerContainer.isRunning()) listenerContainer.start();
        listenerRegistry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) container.start();
        });
    }

    @Test
    void requeuesAFailedJobAndCompletesItOnTheSecondAttempt() throws Exception {
        Long jobId = upload("partitura.pdf");
        QueueUploadFiles completed = await(() -> terminalJob(jobId));
        assertThat(completed.getStatus()).isEqualTo(UploadFileStatusEnum.DONE);

        // Lo stesso job riportato in errore, come dopo un'elaborazione non riuscita: il nuovo
        // tentativo riusa il file già acquisito e arriva di nuovo a DONE.
        transactionExecutor.execute(tenant, () ->
            jobRepository.transitionStatus(jobId, UploadFileStatusEnum.DONE, UploadFileStatusEnum.ERROR, "test")
        );
        QueueUploadFilesDTO retried = transactionExecutor.execute(tenant, () ->
            tracksService.retryUploadJob(completed.getTrackId(), jobId, authentication(tenant))
        );

        assertThat(retried.getStatus()).isEqualTo(UploadFileStatusEnum.TO_PROCESS);
        assertThat(await(() -> terminalJob(jobId)).getStatus()).isEqualTo(UploadFileStatusEnum.DONE);
    }

    /**
     * Il worker si è arrestato con il job in elaborazione: il recupero lo chiude in errore, così
     * l'utente lo vede e può riprovarlo. La soglia è nel passato per rendere il job già fermo.
     */
    @Test
    void closesAJobLeftInProgressByAStoppedWorker() {
        Long jobId = transactionExecutor.execute(tenant, () -> insertJob(UploadFileStatusEnum.IN_PROGRESS));

        recovery.recover(tenant, Date.from(Instant.now().plus(Duration.ofMinutes(1))));

        assertThat(job(jobId).getStatus()).isEqualTo(UploadFileStatusEnum.ERROR);
    }

    @Test
    void keepsTheJobsOfOneTenantInvisibleToAnother() {
        Long jobId = transactionExecutor.execute(tenant, () -> insertJob(UploadFileStatusEnum.IN_PROGRESS));

        transactionExecutor.execute(otherTenant, () -> {
            assertThat(jobRepository.findById(jobId)).isEmpty();
            assertThat(jobRepository.countByStatusAndDeletedFalse(UploadFileStatusEnum.IN_PROGRESS)).isZero();
            assertThat(
                jobRepository.findStale(
                    UploadFileStatusEnum.IN_PROGRESS,
                    Date.from(Instant.now().plus(Duration.ofMinutes(1))),
                    org.springframework.data.domain.PageRequest.of(0, 10)
                )
            ).isEmpty();
        });

        // Il recupero di un tenant non tocca i job dell'altro.
        recovery.recover(otherTenant, Date.from(Instant.now().plus(Duration.ofMinutes(1))));
        assertThat(job(jobId).getStatus()).isEqualTo(UploadFileStatusEnum.IN_PROGRESS);
    }

    /** Carica un PDF vero sulla traccia creata dalla richiesta e restituisce l'id del job. */
    private Long upload(String fileName) throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/pdf", onePagePdf());
        return transactionExecutor.execute(tenant, () ->
            tracksService.uploadFile(null, file, null, authentication(tenant)).getId()
        );
    }

    private QueueUploadFiles job(Long jobId) {
        return transactionExecutor.execute(tenant, () -> jobRepository.findById(jobId).orElseThrow());
    }

    /** Il job quando ha raggiunto uno stato terminale, altrimenti {@code null}. */
    private QueueUploadFiles terminalJob(Long jobId) {
        QueueUploadFiles current = job(jobId);
        boolean terminal = current.getStatus() == UploadFileStatusEnum.DONE || current.getStatus() == UploadFileStatusEnum.ERROR;
        return terminal ? current : null;
    }

    /**
     * Un job con le sue righe obbligatorie, costruito in SQL: alle prove di recupero serve soltanto
     * il suo stato, non un file vero nello storage.
     */
    private Long insertJob(UploadFileStatusEnum status) {
        Long userId = jdbcTemplate.queryForObject(
            "select id from app_user where keycloak_id = ?", Long.class, subject);
        Long mediaId = jdbcTemplate.queryForObject(
            "insert into media_asset (name, storage_key, mime_type, original_filename, file_extension, file_size, sha256, status)"
                + " values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            Long.class, "score.pdf", tenant + "/score.pdf", "application/pdf", "score.pdf", "pdf", 1024L,
            "0".repeat(64), "READY");
        return jdbcTemplate.queryForObject(
            "insert into upload_job (user_id, source_media_asset_id, name, status, type) values (?, ?, ?, ?, ?) returning id",
            Long.class, userId, mediaId, "score.pdf", status.name(), "Tracks");
    }

    private <T> T await(Callable<T> probe) throws Exception {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            T value = probe.call();
            if (value != null) return value;
            Thread.sleep(250);
        }
        throw new AssertionError("Il job non ha raggiunto uno stato terminale entro " + TIMEOUT);
    }

    private void registerTenant(String code) {
        Date now = new Date();
        Tenants registered = new Tenants();
        registered.setCode(code);
        registered.setName(code);
        registered.setActive(true);
        registered.setDeleted(false);
        registered.setInsertBy("upload-pipeline-user");
        registered.setInsertDate(now);
        registered.setEditBy("upload-pipeline-user");
        registered.setEditDate(now);
        registered.setEntityVersion(0L);
        tenantsRegistry.save(registered);
    }

    private void unregisterTenant(String code) {
        tenantsRegistry.findByCodeAndDeletedFalse(code).ifPresent(tenantsRegistry::delete);
    }

    private static byte[] onePagePdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private JwtAuthenticationToken authentication(String tenantCode) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject)
            .claim("tenant", tenantCode)
            .claim("preferred_username", subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
