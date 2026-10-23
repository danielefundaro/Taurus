package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingIdentityOperation;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingJobStatus;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingIdentityOperationRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportIssueRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportRowRepository;
import com.fundaro.zodiac.taurus.service.MediaService;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention of the imports: the source file, the staging rows and the problems of a concluded import are removed after
 * the source retention, leaving counts, hash, author, outcome and an identity journal reduced to the outcomes; the
 * record of the import goes after the audit retention. An import waiting for the compensation is never touched.
 */
@Service
public class OnboardingRetentionService {
    static final Set<OnboardingJobStatus> CONCLUDED = EnumSet.of(
        OnboardingJobStatus.INVALID,
        OnboardingJobStatus.CANCELLED,
        OnboardingJobStatus.FAILED,
        OnboardingJobStatus.COMPLETED
    );

    private final OnboardingImportJobRepository jobs;
    private final OnboardingImportRowRepository rows;
    private final OnboardingImportIssueRepository issues;
    private final OnboardingIdentityOperationRepository identities;
    private final MediaService media;

    public OnboardingRetentionService(
        OnboardingImportJobRepository jobs,
        OnboardingImportRowRepository rows,
        OnboardingImportIssueRepository issues,
        OnboardingIdentityOperationRepository identities,
        MediaService media
    ) {
        this.jobs = jobs;
        this.rows = rows;
        this.issues = issues;
        this.identities = identities;
        this.media = media;
    }

    @Transactional
    public int removeSources(ZonedDateTime completedBefore, String tenantCode, String actor) {
        List<OnboardingImportJob> due = jobs.findAllByStatusInAndSourceRemovedAtIsNullAndCompletedAtBefore(CONCLUDED, completedBefore);
        for (OnboardingImportJob job : due) {
            List<OnboardingIdentityOperation> journal = identities.findAllByJob_Id(job.getId());
            journal.forEach(operation -> {
                operation.setRow(null);
                operation.setPreviousRoles(null);
            });
            identities.saveAll(journal);
            issues.deleteAllByJob_Id(job.getId());
            rows.deleteAllByJob_Id(job.getId());
            job.setSourceRemovedAt(ZonedDateTime.now());
            // Flushed before the file is released: the media reference check reads the database.
            jobs.saveAndFlush(job);
            media.deleteIfUnreferenced(job.getSourceMediaAsset().getId(), tenantCode, actor);
        }
        return due.size();
    }

    @Transactional
    public int removeRecords(ZonedDateTime completedBefore) {
        List<OnboardingImportJob> due = jobs.findAllBySourceRemovedAtIsNotNullAndCompletedAtBefore(completedBefore);
        // The database removes the section counts and the identity journal together with the import.
        jobs.deleteAllInBatch(due);
        return due.size();
    }
}
