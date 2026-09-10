package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Tracks;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TracksRepository extends CatalogRepository<Tracks> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select track from Tracks track where track.id = :id and track.deleted = false")
    Optional<Tracks> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(
        value = """
            WITH active_scores AS (
                SELECT id, -ROW_NUMBER() OVER (ORDER BY display_order, id)::integer AS temporary_order
                FROM sheet_music
                WHERE track_id = :trackId AND deleted = FALSE
            )
            UPDATE sheet_music score
            SET display_order = active_scores.temporary_order
            FROM active_scores
            WHERE score.id = active_scores.id
            """,
        nativeQuery = true
    )
    int moveActiveScoreOrdersToTemporaryRange(@Param("trackId") Long trackId);

    @Modifying
    @Query(
        value = """
            UPDATE sheet_music
            SET display_order = :displayOrder
            WHERE id = :scoreId AND track_id = :trackId AND deleted = FALSE
            """,
        nativeQuery = true
    )
    int restoreActiveScoreOrder(
        @Param("trackId") Long trackId,
        @Param("scoreId") Long scoreId,
        @Param("displayOrder") int displayOrder
    );
}
