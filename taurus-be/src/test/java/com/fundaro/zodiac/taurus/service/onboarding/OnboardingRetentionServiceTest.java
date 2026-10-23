package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingIdentityOperation;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportRow;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingJobStatus;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingIdentityOperationRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportIssueRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportRowRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OnboardingRetentionServiceTest {
    private final OnboardingImportJobRepository jobs = mock(OnboardingImportJobRepository.class);
    private final OnboardingImportRowRepository rows = mock(OnboardingImportRowRepository.class);
    private final OnboardingImportIssueRepository issues = mock(OnboardingImportIssueRepository.class);
    private final OnboardingIdentityOperationRepository identities = mock(OnboardingIdentityOperationRepository.class);
    private final MediaService media = mock(MediaService.class);
    private final OnboardingRetentionService retention = new OnboardingRetentionService(jobs, rows, issues, identities, media);

    @Test
    void removesFileRowsAndProblemsOfTheConcludedImportsKeepingTheReducedJournal() {
        ZonedDateTime before = ZonedDateTime.now().minusDays(30);
        OnboardingImportJob job = job(1L);
        OnboardingIdentityOperation operation = new OnboardingIdentityOperation();
        operation.setJob(job);
        operation.setRow(new OnboardingImportRow());
        operation.setKeycloakId("keycloak-1");
        operation.setPreviousRoles("ROLE_USER");
        when(jobs.findAllByStatusInAndSourceRemovedAtIsNullAndCompletedAtBefore(OnboardingRetentionService.CONCLUDED, before))
            .thenReturn(List.of(job));
        when(identities.findAllByJob_Id(1L)).thenReturn(List.of(operation));

        int removed = retention.removeSources(before, "tenant-a", "onboarding-retention");

        assertThat(removed).isEqualTo(1);
        assertThat(job.getSourceRemovedAt()).isNotNull();
        assertThat(operation.getRow()).isNull();
        assertThat(operation.getPreviousRoles()).isNull();
        assertThat(operation.getKeycloakId()).isEqualTo("keycloak-1");
        InOrder order = inOrder(identities, issues, rows, jobs, media);
        order.verify(identities).saveAll(List.of(operation));
        order.verify(issues).deleteAllByJob_Id(1L);
        order.verify(rows).deleteAllByJob_Id(1L);
        order.verify(jobs).saveAndFlush(job);
        order.verify(media).deleteIfUnreferenced(101L, "tenant-a", "onboarding-retention");
    }

    @Test
    void neverTouchesAnImportWaitingForTheCompensation() {
        assertThat(OnboardingRetentionService.CONCLUDED)
            .containsExactlyInAnyOrder(
                OnboardingJobStatus.INVALID,
                OnboardingJobStatus.CANCELLED,
                OnboardingJobStatus.FAILED,
                OnboardingJobStatus.COMPLETED
            )
            .doesNotContain(OnboardingJobStatus.COMPENSATION_REQUIRED);
    }

    @Test
    void removesTheRecordsOfTheImportsWhoseSourceIsAlreadyGone() {
        ZonedDateTime before = ZonedDateTime.now().minusDays(365);
        OnboardingImportJob job = job(2L);
        when(jobs.findAllBySourceRemovedAtIsNotNullAndCompletedAtBefore(before)).thenReturn(List.of(job));

        int removed = retention.removeRecords(before);

        assertThat(removed).isEqualTo(1);
        verify(jobs).deleteAllInBatch(List.of(job));
        verifyNoInteractions(media);
    }

    private static OnboardingImportJob job(Long id) {
        Media source = new Media();
        source.setId(id + 100);
        OnboardingImportJob job = new OnboardingImportJob();
        job.setId(id);
        job.setStatus(OnboardingJobStatus.COMPLETED);
        job.setSourceMediaAsset(source);
        return job;
    }
}
