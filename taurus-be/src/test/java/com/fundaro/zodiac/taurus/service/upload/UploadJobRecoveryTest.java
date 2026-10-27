package com.fundaro.zodiac.taurus.service.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

/**
 * Il recupero dei job di caricamento fermi: che cosa chiude, che cosa lascia stare e che cosa
 * misura. Il broker non c'è, quindi la profondità della coda resta a -1, che è il valore con cui si
 * distingue «coda vuota» da «broker che non risponde».
 */
class UploadJobRecoveryTest {

    private final QueueUploadFilesRepository repository = mock(QueueUploadFilesRepository.class);
    private final TenantSchemaRegistry registry = mock(TenantSchemaRegistry.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final UploadJobMetrics metrics = new UploadJobMetrics(meters);

    private UploadJobRecovery recovery;

    @BeforeEach
    void buildRecovery() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getUpload().setStaleAfter(Duration.ofMinutes(30));
        recovery = new UploadJobRecovery(properties, repository, registry, directExecutor(), metrics, noAmqpAdmin());
    }

    @Test
    void closesAJobLeftInProgressByAStoppedWorker() {
        when(repository.findStale(eq(UploadFileStatusEnum.IN_PROGRESS), any(Date.class), any(Pageable.class)))
            .thenReturn(List.of(job(7L)));
        when(repository.findStale(eq(UploadFileStatusEnum.TO_PROCESS), any(Date.class), any(Pageable.class)))
            .thenReturn(List.of());
        when(repository.transitionStatus(7L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.ERROR, UploadJobRecovery.SYSTEM_ACTOR))
            .thenReturn(1);

        recovery.recover("BMCDG", limit());

        verify(repository).transitionStatus(7L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.ERROR, UploadJobRecovery.SYSTEM_ACTOR);
        assertThat(operations("CLOSED_STALE")).isEqualTo(1);
        assertThat(failures("STALE_WORKER")).isEqualTo(1);
    }

    @Test
    void closesAJobLeftQueuedByAStoppedBroker() {
        when(repository.findStale(eq(UploadFileStatusEnum.IN_PROGRESS), any(Date.class), any(Pageable.class)))
            .thenReturn(List.of());
        when(repository.findStale(eq(UploadFileStatusEnum.TO_PROCESS), any(Date.class), any(Pageable.class)))
            .thenReturn(List.of(job(9L)));
        when(repository.transitionStatus(9L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.ERROR, UploadJobRecovery.SYSTEM_ACTOR))
            .thenReturn(1);

        recovery.recover("BMCDG", limit());

        assertThat(failures("STALE_QUEUE")).isEqualTo(1);
    }

    /** Il job è stato chiuso da qualcun altro fra la lettura e l'aggiornamento: niente da contare. */
    @Test
    void doesNotCountAJobSomeoneElseHasAlreadyClosed() {
        when(repository.findStale(eq(UploadFileStatusEnum.IN_PROGRESS), any(Date.class), any(Pageable.class)))
            .thenReturn(List.of(job(7L)));
        when(repository.findStale(eq(UploadFileStatusEnum.TO_PROCESS), any(Date.class), any(Pageable.class)))
            .thenReturn(List.of());
        when(repository.transitionStatus(7L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.ERROR, UploadJobRecovery.SYSTEM_ACTOR))
            .thenReturn(0);

        recovery.recover("BMCDG", limit());

        assertThat(operations("CLOSED_STALE")).isZero();
    }

    @Test
    void measuresPendingJobsAndTheAgeOfTheOldestAcross()  {
        when(registry.findActiveTenantCodes()).thenReturn(List.of("BMCDG", "ALTRO"));
        when(repository.findStale(any(), any(Date.class), any(Pageable.class))).thenReturn(List.of());
        when(repository.countByStatusAndDeletedFalse(UploadFileStatusEnum.TO_PROCESS)).thenReturn(2L);
        when(repository.countByStatusAndDeletedFalse(UploadFileStatusEnum.IN_PROGRESS)).thenReturn(1L);
        when(repository.findOldestAcceptance(any())).thenReturn(Date.from(Instant.now().minus(Duration.ofMinutes(10))));

        recovery.recoverAllTenants();

        // Due tenant, quindi i conteggi si sommano; l'anzianità è quella del job più vecchio.
        assertThat(gauge("taurus.upload.jobs.pending", "status", "TO_PROCESS")).isEqualTo(4);
        assertThat(gauge("taurus.upload.jobs.pending", "status", "IN_PROGRESS")).isEqualTo(2);
        assertThat(meters.find("taurus.upload.jobs.oldest.age").gauge().value()).isBetween(590.0, 700.0);
        assertThat(meters.find("taurus.upload.queue.messages").gauge().value()).isEqualTo(-1);
    }

    @Test
    void keepsGoingWhenOneTenantFails() {
        when(registry.findActiveTenantCodes()).thenReturn(List.of("ROTTO", "BUONO"));
        when(repository.findStale(any(), any(Date.class), any(Pageable.class)))
            .thenThrow(new IllegalStateException("schema non raggiungibile"))
            .thenReturn(List.of());
        when(repository.countByStatusAndDeletedFalse(any())).thenReturn(0L);

        recovery.recoverAllTenants();

        assertThat(meters.find("taurus.upload.jobs.pending").gauges()).isNotEmpty();
    }

    @Test
    void doesNothingWhenRecoveryIsDisabled() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getUpload().setRecoveryEnabled(false);
        new UploadJobRecovery(properties, repository, registry, directExecutor(), metrics, noAmqpAdmin()).recoverAllTenants();

        verify(registry, never()).findActiveTenantCodes();
    }

    private static QueueUploadFiles job(long id) {
        QueueUploadFiles job = new QueueUploadFiles();
        job.setId(id);
        job.setName("score.pdf");
        return job;
    }

    private static Date limit() {
        return Date.from(Instant.now().minus(Duration.ofMinutes(30)));
    }

    private double operations(String operation) {
        return meters.counter("taurus.upload.jobs", "operation", operation).count();
    }

    private double failures(String reason) {
        return meters.counter("taurus.upload.failures", "reason", reason).count();
    }

    private double gauge(String name, String tag, String value) {
        return meters.find(name).tag(tag, value).gauge().value();
    }

    /** Esegue l'azione subito, senza transazione e senza cambiare schema. */
    private static TenantTransactionExecutor directExecutor() {
        return new TenantTransactionExecutor(null) {
            @Override
            public void execute(String tenantCode, Runnable action) {
                action.run();
            }

            @Override
            public <T> T execute(String tenantCode, Supplier<T> action) {
                return action.get();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AmqpAdmin> noAmqpAdmin() {
        ObjectProvider<AmqpAdmin> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
