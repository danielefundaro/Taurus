package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;

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
    private final TenantSchemaRegistry schemas;
    private final TenantTransactionExecutor transactions;
    private final OnboardingImportJobRepository jobs;
    private final MediaService media;
    private final OnboardingValidationService validation;
    private final OnboardingWorker worker;
    private TenantFeatureService tenantFeatureService;

    public OnboardingRecoveryScheduler(TenantSchemaRegistry schemas, TenantTransactionExecutor transactions, OnboardingImportJobRepository jobs, MediaService media, OnboardingValidationService validation, OnboardingWorker worker) {
        this.schemas = schemas;
        this.transactions = transactions;
        this.jobs = jobs;
        this.media = media;
        this.validation = validation;
        this.worker = worker;
    }

    @Autowired
    void setTenantFeatureService(TenantFeatureService value) {
        tenantFeatureService = value;
    }

    @Scheduled(fixedDelayString = "${application.onboarding.worker-delay:2000}")
    public void resumeUploadedJobs() {
        for (String tenant : schemas.findActiveTenantCodes()) {
            try {
                var recoverable = transactions.execute(
                    tenant,
                    () -> jobs.findTop5ByStatusInOrderByInsertDateAsc(
                        List.of(OnboardingJobStatus.UPLOADED, OnboardingJobStatus.VALIDATING, OnboardingJobStatus.APPLYING, OnboardingJobStatus.COMPENSATING)
                    ).stream().map(job -> new RecoveryJob(job.getId(), job.getStatus(), job.getSourceMediaAsset().getId(), job.getExecutedBy())).toList()
                );
                for (RecoveryJob job : recoverable) resume(tenant, job);
            } catch (RuntimeException exception) {
                log.warn("Unable to scan onboarding jobs for tenant {}", tenant);
            }
        }
    }

    private void resume(String tenant, RecoveryJob job) {
        try {
            if ((job.status() == OnboardingJobStatus.UPLOADED || job.status() == OnboardingJobStatus.VALIDATING)
                && tenantFeatureService != null && !tenantFeatureService.isEnabledForTenant(tenant, TenantFeature.ONBOARDING_IMPORT))
                return;
            switch (job.status()) {
                case UPLOADED, VALIDATING -> {
                    MediaService.MediaContent content = media.getContent(job.mediaId(), tenant);
                    validation.validate(job.id(), content.bytes());
                }
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
