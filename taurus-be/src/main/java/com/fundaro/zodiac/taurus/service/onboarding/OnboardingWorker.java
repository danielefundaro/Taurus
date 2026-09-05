package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.onboarding.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OnboardingWorker {
    private final OnboardingValidationService validation;
    private final OnboardingImportJobRepository jobs;
    private final OnboardingImportRowRepository rows;
    private final OnboardingIdentitySagaService identities;
    private final OnboardingDomainApplicationService domains;

    public OnboardingWorker(OnboardingValidationService validation, OnboardingImportJobRepository jobs, OnboardingImportRowRepository rows,
        OnboardingIdentitySagaService identities, OnboardingDomainApplicationService domains) {
        this.validation = validation; this.jobs = jobs; this.rows = rows; this.identities = identities; this.domains = domains;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void validate(OnboardingWorkEvents.Validate event) {
        TenantContext.run(event.tenantCode(), () -> validation.validate(event.jobId(), event.content()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void apply(OnboardingWorkEvents.Apply event) {
        TenantContext.run(event.tenantCode(), () -> applyCurrentTenant(event));
    }

    private void applyCurrentTenant(OnboardingWorkEvents.Apply event) {
        OnboardingImportJob job = jobs.findByIdAndDeletedFalse(event.jobId()).orElseThrow();
        List<OnboardingImportRow> staged = rows.findAllByJob_IdOrderBySectionAscRowNumberAsc(event.jobId());
        List<OnboardingImportRow> userRows = staged.stream()
            .filter(row -> row.getSection() == OnboardingSection.USERS && row.getAction() != OnboardingRowAction.SKIP)
            .filter(row -> row.getStatus() == OnboardingRowStatus.VALID || row.getStatus() == OnboardingRowStatus.WARNING)
            .toList();
        try {
            Map<Long, String> prepared = identities.prepare(job, userRows, event.tenantCode());
            domains.apply(staged, prepared, event.tenantCode(), event.actor());
            int emailFailures = job.isSendSetupEmails() ? identities.sendSetupEmails(job.getId()) : 0;
            complete(job.getId(), emailFailures);
        } catch (RuntimeException exception) {
            boolean compensated;
            try { compensated = identities.compensate(job.getId(), event.tenantCode()); }
            catch (RuntimeException compensationException) { compensated = false; }
            fail(job.getId(), compensated);
        }
    }

    @Transactional
    public void complete(Long id, int emailFailures) {
        OnboardingImportJob job = jobs.findByIdAndDeletedFalse(id).orElseThrow(); job.setStatus(OnboardingJobStatus.COMPLETED); job.setStage("COMPLETED"); job.setProgressPercentage(100); job.setSetupEmailFailures(emailFailures); job.setCompletedAt(ZonedDateTime.now()); jobs.save(job);
    }

    @Transactional
    public void fail(Long id, boolean compensated) {
        OnboardingImportJob job = jobs.findByIdAndDeletedFalse(id).orElseThrow(); job.setStatus(compensated ? OnboardingJobStatus.FAILED : OnboardingJobStatus.COMPENSATION_REQUIRED); job.setStage(compensated ? "COMPENSATED" : "COMPENSATION_REQUIRED"); job.setLastErrorCode(compensated ? "APPLICATION_FAILED" : "COMPENSATION_INCOMPLETE"); job.setCompletedAt(ZonedDateTime.now()); jobs.save(job);
    }
}
