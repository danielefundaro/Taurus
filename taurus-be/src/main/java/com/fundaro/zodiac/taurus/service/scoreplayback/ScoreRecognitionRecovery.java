package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.config.ApplicationProperties.OmrProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJob;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreRecognitionJobRepository;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps the recognition jobs moving when a worker stops or a message is lost. A job running for longer than
 * {@code stale-after} is queued again, until it has been tried {@code max-attempts} times; a job queued for longer than
 * {@code stale-after} is published again. Duplicated messages are harmless: only one worker can take a job.
 */
@Component
public class ScoreRecognitionRecovery {

    private static final Logger log = LoggerFactory.getLogger(ScoreRecognitionRecovery.class);

    private final OmrProperties properties;
    private final ScoreRecognitionJobRepository jobRepository;
    private final ScoreRecognitionQueue queue;
    private final ScoreRecognitionWorker worker;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantFeatureService tenantFeatureService;
    private final TenantTransactionExecutor transactions;

    public ScoreRecognitionRecovery(
        ApplicationProperties properties,
        ScoreRecognitionJobRepository jobRepository,
        ScoreRecognitionQueue queue,
        ScoreRecognitionWorker worker,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantFeatureService tenantFeatureService,
        TenantTransactionExecutor transactions
    ) {
        this.properties = properties.getScorePlayback().getOmr();
        this.jobRepository = jobRepository;
        this.queue = queue;
        this.worker = worker;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantFeatureService = tenantFeatureService;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${application.score-playback.omr.recovery-delay:PT1M}", initialDelayString = "PT1M")
    public void recoverAllTenants() {
        if (!properties.isConfigured() || !properties.isRecoveryEnabled()) return;
        ZonedDateTime now = ZonedDateTime.now();
        for (String tenant : tenantSchemaRegistry.findActiveTenantCodes()) {
            if (!tenantFeatureService.isEnabledForTenant(tenant, TenantFeature.SCORE_PLAYBACK)) continue;
            try {
                recover(tenant, now);
            } catch (RuntimeException exception) {
                log.warn("Recognition jobs of tenant {} not recovered: {}", tenant, exception.getMessage());
            }
        }
    }

    /** Recovers the jobs of one tenant as of {@code now}. */
    public void recover(String tenant, ZonedDateTime now) {
        ZonedDateTime limit = now.minus(properties.getStaleAfter());
        transactions.execute(tenant, () -> {
            Set<Long> requeued = new HashSet<>();
            for (ScoreRecognitionJob job : jobRepository.findAllByStatusAndStartedAtBeforeAndDeletedFalse(ScoreRecognitionJobStatus.RUNNING, limit)) {
                if (job.getAttempts() >= properties.getMaxAttempts()) {
                    log.warn("Recognition job {} interrupted {} times: failed", job.getId(), job.getAttempts());
                    worker.markFailed(job, ScoreRecognitionWorker.INTERRUPTED);
                } else {
                    log.warn("Recognition job {} interrupted: queued again", job.getId());
                    job.setStatus(ScoreRecognitionJobStatus.QUEUED);
                    job.setStartedAt(null);
                    job.touchAudit(ScoreRecognitionWorker.SYSTEM);
                    queue.publishAfterCommit(tenant, job.getId());
                    requeued.add(job.getId());
                }
            }
            for (ScoreRecognitionJob job : jobRepository.findAllByStatusAndEditDateBeforeAndDeletedFalse(ScoreRecognitionJobStatus.QUEUED, limit)) {
                if (requeued.contains(job.getId())) continue;
                log.info("Recognition job {} still queued: published again", job.getId());
                job.touchAudit(ScoreRecognitionWorker.SYSTEM);
                queue.publishAfterCommit(tenant, job.getId());
            }
        });
    }
}
