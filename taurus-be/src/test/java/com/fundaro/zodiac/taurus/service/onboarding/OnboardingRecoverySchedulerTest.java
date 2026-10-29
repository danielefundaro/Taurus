package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingJobStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OnboardingRecoverySchedulerTest {
    private final TenantSchemaRegistry schemas = mock(TenantSchemaRegistry.class);
    private final TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
    private final OnboardingImportJobRepository jobs = mock(OnboardingImportJobRepository.class);
    private final MediaService media = mock(MediaService.class);
    private final OnboardingValidationService validation = mock(OnboardingValidationService.class);
    private final OnboardingWorker worker = mock(OnboardingWorker.class);
    private final ApplicationProperties applicationProperties = new ApplicationProperties();
    private final OnboardingRecoveryScheduler scheduler =
        new OnboardingRecoveryScheduler(schemas, transactions, jobs, media, validation, worker, applicationProperties);

    @Test
    @SuppressWarnings("unchecked")
    void resumesEveryNonTerminalPhaseInItsTenant() {
        when(schemas.findActiveTenantCodes()).thenReturn(List.of("tenant-a"));
        when(transactions.execute(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        when(jobs.findResumable(any(), any(), anyInt())).thenReturn(
            List.of(job(1L, OnboardingJobStatus.VALIDATING, "validator"), job(2L, OnboardingJobStatus.APPLYING, "executor"), job(3L, OnboardingJobStatus.COMPENSATING, "executor"))
        );
        when(media.getContent(101L, "tenant-a")).thenReturn(new MediaService.MediaContent("source.csv", "text/csv", 1, new byte[]{1}));

        scheduler.resumeUploadedJobs();

        verify(validation).validate(eq(1L), aryEq(new byte[]{1}));
        verify(worker).resumeApply(2L, "tenant-a", "executor");
        verify(worker).resumeCompensation(3L, "tenant-a");
    }

    /** A job taken a moment ago is left alone, and the one taken now is stamped so the next scan skips it. */
    @Test
    @SuppressWarnings("unchecked")
    void takesOnlyTheJobsIdleForTheConfiguredTimeAndStampsThem() {
        applicationProperties.getOnboarding().setResumeAfterMillis(60_000);
        when(schemas.findActiveTenantCodes()).thenReturn(List.of("tenant-a"));
        when(transactions.execute(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        OnboardingImportJob applying = job(2L, OnboardingJobStatus.APPLYING, "executor");
        when(jobs.findResumable(any(), any(), anyInt())).thenReturn(List.of(applying));
        ZonedDateTime before = ZonedDateTime.now();

        scheduler.resumeUploadedJobs();

        ArgumentCaptor<ZonedDateTime> idleBefore = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(jobs).findResumable(eq(OnboardingRecoveryScheduler.RESUMABLE), idleBefore.capture(), eq(5));
        assertThat(idleBefore.getValue()).isBefore(before.minusSeconds(59));
        assertThat(applying.getResumeClaimedAt()).isNotNull();
        verify(jobs).saveAll(List.of(applying));
    }

    private static OnboardingImportJob job(Long id, OnboardingJobStatus status, String actor) {
        Media source = new Media();
        source.setId(id + 100);
        OnboardingImportJob job = new OnboardingImportJob();
        job.setId(id);
        job.setStatus(status);
        job.setSourceMediaAsset(source);
        job.setExecutedBy(actor);
        return job;
    }
}
