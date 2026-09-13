package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnboardingImportJobRepository extends JpaRepository<OnboardingImportJob, Long> {
    Optional<OnboardingImportJob> findByRequestedByAndUploadIdempotencyKeyAndDeletedFalse(String requestedBy, UUID key);

    Optional<OnboardingImportJob> findByIdAndDeletedFalse(Long id);

    Page<OnboardingImportJob> findAllByDeletedFalse(Pageable pageable);

    Optional<OnboardingImportJob> findFirstByStatusOrderByCompletedAtDesc(OnboardingJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OnboardingImportJob j where j.id = :id and j.deleted = false")
    Optional<OnboardingImportJob> findForUpdate(@Param("id") Long id);

    boolean existsByStatus(OnboardingJobStatus status);

    List<OnboardingImportJob> findTop5ByStatusOrderByInsertDateAsc(OnboardingJobStatus status);

    List<OnboardingImportJob> findTop5ByStatusInOrderByInsertDateAsc(Collection<OnboardingJobStatus> statuses);
}
