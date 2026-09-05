package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.onboarding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OnboardingRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(OnboardingRecoveryScheduler.class);
    private final TenantSchemaRegistry schemas;
    private final TenantTransactionExecutor transactions;
    private final OnboardingImportJobRepository jobs;
    private final MediaService media;
    private final OnboardingValidationService validation;
    public OnboardingRecoveryScheduler(TenantSchemaRegistry schemas, TenantTransactionExecutor transactions, OnboardingImportJobRepository jobs, MediaService media, OnboardingValidationService validation) { this.schemas = schemas; this.transactions = transactions; this.jobs = jobs; this.media = media; this.validation = validation; }

    @Scheduled(fixedDelayString = "${application.onboarding.worker-delay:2000}")
    public void resumeUploadedJobs() {
        for (String tenant : schemas.findActiveTenantCodes()) try { transactions.execute(tenant, () -> jobs.findTop5ByStatusOrderByInsertDateAsc(OnboardingJobStatus.UPLOADED).forEach(job -> {
            try { MediaService.MediaContent content = media.getContent(job.getSourceMediaAsset().getId(), tenant); validation.validate(job.getId(), content.bytes()); }
            catch (RuntimeException exception) { log.warn("Unable to resume onboarding job {} for tenant {}", job.getId(), tenant); }
        })); } catch (RuntimeException exception) { log.warn("Unable to scan onboarding jobs for tenant {}", tenant); }
    }
}
