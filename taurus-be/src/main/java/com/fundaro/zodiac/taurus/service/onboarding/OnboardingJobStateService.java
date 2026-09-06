package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingJobStatus;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingJobStateService {
    private final OnboardingImportJobRepository jobs;

    public OnboardingJobStateService(OnboardingImportJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensating(Long id) {
        OnboardingImportJob job = required(id);
        job.setStatus(OnboardingJobStatus.COMPENSATING);
        job.setStage("COMPENSATING_IDENTITIES");
        job.setLastErrorCode("APPLICATION_FAILED");
        jobs.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long id, int emailFailures) {
        OnboardingImportJob job = required(id);
        job.setStatus(OnboardingJobStatus.COMPLETED);
        job.setStage("COMPLETED");
        job.setProgressPercentage(100);
        job.setSetupEmailFailures(emailFailures);
        job.setCompletedAt(ZonedDateTime.now());
        jobs.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long id, boolean compensated) {
        OnboardingImportJob job = required(id);
        job.setStatus(compensated ? OnboardingJobStatus.FAILED : OnboardingJobStatus.COMPENSATION_REQUIRED);
        job.setStage(compensated ? "COMPENSATED" : "COMPENSATION_REQUIRED");
        job.setLastErrorCode(compensated ? "APPLICATION_FAILED" : "COMPENSATION_INCOMPLETE");
        job.setCompletedAt(ZonedDateTime.now());
        jobs.save(job);
    }

    private OnboardingImportJob required(Long id) {
        return jobs.findByIdAndDeletedFalse(id).orElseThrow();
    }
}
