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

import java.time.ZonedDateTime;
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

    long countByRequestedByAndStatusInAndDeletedFalse(String requestedBy, Collection<OnboardingJobStatus> statuses);

    List<OnboardingImportJob> findTop5ByStatusOrderByInsertDateAsc(OnboardingJobStatus status);

    List<OnboardingImportJob> findTop5ByStatusInOrderByInsertDateAsc(Collection<OnboardingJobStatus> statuses);

    /**
     * Serializes the application of the imports of the tenant. The lock is released at the end of the transaction and
     * makes the check on the job already applying atomic: without it two replicas can both read no job applying.
     *
     * @return whether the lock was free
     */
    @Query(value = "select pg_try_advisory_xact_lock(hashtextextended(current_schema() || ':onboarding-apply', 0))", nativeQuery = true)
    boolean tryLockApply();

    /**
     * The jobs in flight that nobody has taken for a while, taken for this replica alone: {@code skip locked} leaves
     * to another reader the rows already locked, and the caller stamps {@code resume_claimed_at} before committing, so
     * the next scan does not take them again until the idle time has passed.
     */
    @Query(
        value = "select * from onboarding_import_job" +
            " where deleted = false and status in (:statuses)" +
            " and coalesce(resume_claimed_at, edit_date) < :idleBefore" +
            " order by insert_date limit :limit for update skip locked",
        nativeQuery = true
    )
    List<OnboardingImportJob> findResumable(
        @Param("statuses") Collection<String> statuses,
        @Param("idleBefore") ZonedDateTime idleBefore,
        @Param("limit") int limit
    );

    List<OnboardingImportJob> findAllByStatusInAndSourceRemovedAtIsNullAndCompletedAtBefore(
        Collection<OnboardingJobStatus> statuses,
        ZonedDateTime completedBefore
    );

    List<OnboardingImportJob> findAllBySourceRemovedAtIsNotNullAndCompletedAtBefore(ZonedDateTime completedBefore);

    /** The imports nobody has touched since the given time: their author has left them without a decision. */
    List<OnboardingImportJob> findAllByStatusInAndDeletedFalseAndEditDateBefore(
        Collection<OnboardingJobStatus> statuses,
        ZonedDateTime editedBefore
    );
}
