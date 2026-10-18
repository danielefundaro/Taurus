package com.fundaro.zodiac.taurus.repository.scoreplayback;

import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJob;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreRecognitionJobRepository extends JpaRepository<ScoreRecognitionJob, Long> {

    Optional<ScoreRecognitionJob> findByIdAndDeletedFalse(Long id);

    List<ScoreRecognitionJob> findAllByTrackIdAndDeletedFalseOrderByIdDesc(Long trackId);

    List<ScoreRecognitionJob> findAllByBatchIdAndDeletedFalse(UUID batchId);

    boolean existsBySheetMusicIdAndStatusInAndDeletedFalse(Long sheetMusicId, Collection<ScoreRecognitionJobStatus> statuses);

    /**
     * Takes a queued job for execution. Only one consumer wins when the same job is delivered twice.
     *
     * @return 1 when the job was queued and is now running
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update ScoreRecognitionJob job set job.status = com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus.RUNNING, " +
            "job.attempts = job.attempts + 1, job.startedAt = :now, job.editBy = :actor, job.editDate = :now, " +
            "job.entityVersion = job.entityVersion + 1 " +
            "where job.id = :id and job.deleted = false and job.status = com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus.QUEUED"
    )
    int claim(@Param("id") Long id, @Param("now") ZonedDateTime now, @Param("actor") String actor);

    /** Running jobs started before the given time: the worker that took them stopped or lost them. */
    List<ScoreRecognitionJob> findAllByStatusAndStartedAtBeforeAndDeletedFalse(ScoreRecognitionJobStatus status, ZonedDateTime startedBefore);

    /** Queued jobs not touched since the given time: their message may have been lost. */
    List<ScoreRecognitionJob> findAllByStatusAndEditDateBeforeAndDeletedFalse(ScoreRecognitionJobStatus status, ZonedDateTime editedBefore);
}
