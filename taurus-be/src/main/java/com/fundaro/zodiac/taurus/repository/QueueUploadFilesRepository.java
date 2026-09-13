package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
