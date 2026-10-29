package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.onboarding", name = {"enabled", "recovery-enabled"}, havingValue = "true", matchIfMissing = true)
public class OnboardingRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(OnboardingRecoveryScheduler.class);
    static final List<String> RESUMABLE = List.of(
        OnboardingJobStatus.UPLOADED.name(),
        OnboardingJobStatus.VALIDATING.name(),
        OnboardingJobStatus.APPLYING.name(),
        OnboardingJobStatus.COMPENSATING.name()
    );
    private final TenantSchemaRegistry schemas;
    private final TenantTransactionExecutor transactions;
    private final OnboardingImportJobRepository jobs;
    private final MediaService media;
    private final OnboardingValidationService validation;
    private final OnboardingWorker worker;
    private final ApplicationProperties.OnboardingProperties properties;
    private TenantFeatureService tenantFeatureService;

    public OnboardingRecoveryScheduler(TenantSchemaRegistry schemas, TenantTransactionExecutor transactions, OnboardingImportJobRepository jobs, MediaService media, OnboardingValidationService validation, OnboardingWorker worker, ApplicationProperties applicationProperties) {
        this.schemas = schemas;
        this.transactions = transactions;
        this.jobs = jobs;
        this.media = media;
        this.validation = validation;
        this.worker = worker;
        this.properties = applicationProperties.getOnboarding();
    }

    @Autowired
    void setTenantFeatureService(TenantFeatureService value) {
        tenantFeatureService = value;
    }

    @Scheduled(fixedDelayString = "${application.onboarding.worker-delay:2000}")
    public void resumeUploadedJobs() {
        ZonedDateTime idleBefore = ZonedDateTime.now().minus(Duration.ofMillis(properties.getResumeAfterMillis()));
        for (String tenant : schemas.findActiveTenantCodes()) {
            try {
                for (RecoveryJob job : transactions.execute(tenant, () -> claim(idleBefore))) resume(tenant, job);
            } catch (RuntimeException exception) {
                log.warn("Unable to scan onboarding jobs for tenant {}", tenant);
            }
        }
    }

    /**
     * Takes the jobs in flight that nobody has touched for the idle time. The selection locks them and skips the rows
     * another reader holds; stamping the moment they were taken keeps the next scans off them, so an import still
     * being validated or applied is not started a second time.
     */
    private List<RecoveryJob> claim(ZonedDateTime idleBefore) {
        List<OnboardingImportJob> taken = jobs.findResumable(RESUMABLE, idleBefore, properties.getWorkerBatchSize());
        ZonedDateTime now = ZonedDateTime.now();
        taken.forEach(job -> job.setResumeClaimedAt(now));
        jobs.saveAll(taken);
        return taken.stream()
            .map(job -> new RecoveryJob(job.getId(), job.getStatus(), job.getSourceMediaAsset().getId(), job.getExecutedBy()))
            .toList();
    }

    private void resume(String tenant, RecoveryJob job) {
        try {
            if ((job.status() == OnboardingJobStatus.UPLOADED || job.status() == OnboardingJobStatus.VALIDATING)
                && tenantFeatureService != null && !tenantFeatureService.isEnabledForTenant(tenant, TenantFeature.ONBOARDING_IMPORT))
                return;
            switch (job.status()) {
                // La validazione legge e scrive nello schema del tenant: senza il contesto lavorerebbe su «public».
                case UPLOADED, VALIDATING -> TenantContext.run(tenant, () -> {
                    MediaService.MediaContent content = media.getContent(job.mediaId(), tenant);
                    validation.validate(job.id(), content.bytes());
                });
                case APPLYING -> worker.resumeApply(job.id(), tenant, job.actor());
                case COMPENSATING -> worker.resumeCompensation(job.id(), tenant);
                default -> {
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to resume onboarding job {} for tenant {}", job.id(), tenant);
        }
    }

    private record RecoveryJob(Long id, OnboardingJobStatus status, Long mediaId, String actor) {
    }
}
