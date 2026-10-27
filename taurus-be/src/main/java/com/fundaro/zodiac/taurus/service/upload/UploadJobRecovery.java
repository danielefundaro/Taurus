package com.fundaro.zodiac.taurus.service.upload;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.config.ApplicationProperties.UploadProperties;
import com.fundaro.zodiac.taurus.config.RabbitMQConfig;
import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rileva i job di caricamento fermi e li chiude in errore, poi misura la coda.
 *
 * <p>Un worker che si arresta lascia il job in {@code IN_PROGRESS}: il messaggio riconsegnato viene
 * ignorato, perché la presa in carico è condizionata a {@code TO_PROCESS}, e senza questo giro il
 * job resterebbe «Elaborazione in corso» per sempre. Un broker non raggiungibile lo lascia invece
 * in {@code TO_PROCESS}.
 *
 * <p>In entrambi i casi la scelta è chiudere in {@code ERROR}, non riaccodare: il messaggio porta
 * il token di chi ha caricato il file, quindi riaccodare significherebbe fabbricare un'identità di
 * sistema con i permessi di scrittura di un utente. Chiuso il job, l'interfaccia mostra
 * «Elaborazione non riuscita» con «Riprova», e il nuovo tentativo riparte dalla richiesta di chi
 * ha caricato il file, con il suo token. Il messaggio eventualmente ancora in coda è innocuo:
 * trovando il job fuori da {@code TO_PROCESS} il worker lo scarta.
 *
 * <p>La soglia deve restare più larga della conversione più lenta plausibile. Un job chiuso mentre
 * il worker sta ancora lavorando produce comunque le sue parti, ma non raggiunge {@code DONE}: il
 * worker lo registra come conclusione tardiva e un nuovo tentativo dell'utente duplicherebbe le
 * parti. È il motivo per cui la soglia predefinita è di trenta minuti e non di cinque.
 */
@Component
public class UploadJobRecovery {

    public static final String SYSTEM_ACTOR = "upload-job-recovery";
    private static final Logger log = LoggerFactory.getLogger(UploadJobRecovery.class);
    private static final List<UploadFileStatusEnum> PENDING = List.of(
        UploadFileStatusEnum.TO_PROCESS,
        UploadFileStatusEnum.IN_PROGRESS
    );

    private final UploadProperties properties;
    private final QueueUploadFilesRepository repository;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor transactions;
    private final UploadJobMetrics metrics;
    private final ObjectProvider<AmqpAdmin> amqpAdmin;

    public UploadJobRecovery(
        ApplicationProperties properties,
        QueueUploadFilesRepository repository,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor transactions,
        UploadJobMetrics metrics,
        ObjectProvider<AmqpAdmin> amqpAdmin
    ) {
        this.properties = properties.getUpload();
        this.repository = repository;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.transactions = transactions;
        this.metrics = metrics;
        this.amqpAdmin = amqpAdmin;
    }

    @Scheduled(fixedDelayString = "${application.upload.recovery-delay:PT5M}", initialDelayString = "PT1M")
    public void recoverAllTenants() {
        if (!properties.isRecoveryEnabled()) return;
        Date limit = Date.from(Instant.now().minus(properties.getStaleAfter()));
        long queued = 0;
        long running = 0;
        Date oldest = null;
        for (String tenant : tenantSchemaRegistry.findActiveTenantCodes()) {
            try {
                Snapshot snapshot = recover(tenant, limit);
                queued += snapshot.queued();
                running += snapshot.running();
                oldest = earlier(oldest, snapshot.oldestAcceptance());
            } catch (RuntimeException exception) {
                log.warn("Job di caricamento del tenant {} non recuperati: {}", tenant, exception.getMessage());
            }
        }
        metrics.recordPending(queued, running, oldest == null ? 0 : ageInSeconds(oldest));
        metrics.recordQueueDepth(queueDepth());
    }

    /** Recupera i job di un tenant e restituisce quel che resta non terminale. */
    public Snapshot recover(String tenant, Date limit) {
        return transactions.execute(tenant, () -> {
            close(UploadFileStatusEnum.IN_PROGRESS, limit, UploadJobMetrics.FailureReason.STALE_WORKER);
            close(UploadFileStatusEnum.TO_PROCESS, limit, UploadJobMetrics.FailureReason.STALE_QUEUE);
            return new Snapshot(
                repository.countByStatusAndDeletedFalse(UploadFileStatusEnum.TO_PROCESS),
                repository.countByStatusAndDeletedFalse(UploadFileStatusEnum.IN_PROGRESS),
                repository.findOldestAcceptance(PENDING)
            );
        });
    }

    private void close(UploadFileStatusEnum status, Date limit, UploadJobMetrics.FailureReason reason) {
        List<QueueUploadFiles> stale = repository.findStale(status, limit, PageRequest.of(0, properties.getMaxRecoveredPerRun()));
        for (QueueUploadFiles job : stale) {
            if (repository.transitionStatus(job.getId(), status, UploadFileStatusEnum.ERROR, SYSTEM_ACTOR) != 1) continue;
            log.warn("Job di caricamento {} fermo in {} oltre la soglia: chiuso in errore", job.getId(), status);
            metrics.record(UploadJobMetrics.Operation.CLOSED_STALE);
            metrics.recordFailure(reason);
        }
    }

    /** I messaggi ancora in coda, o -1 quando il broker non risponde o non espone la coda. */
    private long queueDepth() {
        AmqpAdmin admin = amqpAdmin.getIfAvailable();
        if (admin == null) return -1;
        try {
            Properties queue = admin.getQueueProperties(RabbitMQConfig.queueNameListener);
            if (queue == null) return -1;
            Object count = queue.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            return count instanceof Number number ? number.longValue() : -1;
        } catch (RuntimeException exception) {
            log.debug("Coda {} non interrogabile: {}", RabbitMQConfig.queueNameListener, exception.getMessage());
            return -1;
        }
    }

    private static Date earlier(Date current, Date candidate) {
        if (candidate == null) return current;
        return current == null || candidate.before(current) ? candidate : current;
    }

    private static long ageInSeconds(Date acceptance) {
        return Math.max(Duration.between(acceptance.toInstant(), Instant.now()).toSeconds(), 0);
    }

    /** Quel che resta non terminale in un tenant dopo il recupero. */
    public record Snapshot(long queued, long running, Date oldestAcceptance) {}
}
