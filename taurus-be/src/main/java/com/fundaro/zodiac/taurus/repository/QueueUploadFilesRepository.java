package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface QueueUploadFilesRepository extends CatalogRepository<QueueUploadFiles> {
    List<QueueUploadFiles> findAllByUser_KeycloakId(String keycloakId);

    List<QueueUploadFiles> findAllByTrack_IdAndDeletedFalseOrderByInsertDateDesc(Long trackId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update QueueUploadFiles job set job.status = :target, job.editBy = :actor, job.editDate = current_timestamp, " +
            "job.entityVersion = job.entityVersion + 1 where job.id = :id and job.deleted = false and job.status = :expected"
    )
    int transitionStatus(
        @Param("id") Long id,
        @Param("expected") UploadFileStatusEnum expected,
        @Param("target") UploadFileStatusEnum target,
        @Param("actor") String actor
    );

    /**
     * I job fermi nello stato indicato da prima del limite. L'anzianità nello stato corrente è
     * l'ultima modifica, perché è la transizione a scriverla; un job mai transitato non ha
     * {@code editDate} e vale la data di inserimento.
     */
    @Query(
        "select job from QueueUploadFiles job where job.deleted = false and job.status = :status " +
            "and coalesce(job.editDate, job.insertDate) < :limit order by coalesce(job.editDate, job.insertDate) asc"
    )
    List<QueueUploadFiles> findStale(
        @Param("status") UploadFileStatusEnum status,
        @Param("limit") Date limit,
        Pageable pageable
    );

    long countByStatusAndDeletedFalse(UploadFileStatusEnum status);

    /** L'accettazione del job non terminale più vecchio: l'anzianità della coda si misura da qui. */
    @Query("select min(job.insertDate) from QueueUploadFiles job where job.deleted = false and job.status in :statuses")
    Date findOldestAcceptance(@Param("statuses") Collection<UploadFileStatusEnum> statuses);
}
