package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Tracks;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TracksRepository extends CatalogRepository<Tracks> {

    @Modifying
    @Query(
        value = """
            WITH active_scores AS (
                SELECT id, -ROW_NUMBER() OVER (ORDER BY id)::integer AS temporary_order
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
}
