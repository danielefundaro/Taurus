package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Media;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MediaRepository extends CatalogRepository<Media> {

    @Query("select media.storageKey from Media media where media.deleted = false")
    List<String> findActiveStorageKeys();

    @Query(
        value = """
            SELECT NOT EXISTS (
                SELECT 1 FROM sheet_music_media WHERE media_asset_id = :id AND deleted = FALSE
                UNION ALL
                SELECT 1 FROM upload_job WHERE source_media_asset_id = :id AND deleted = FALSE
                UNION ALL
                SELECT 1 FROM inventory_item_photo WHERE media_asset_id = :id AND deleted = FALSE
                UNION ALL
                SELECT 1 FROM inventory_return_photo WHERE media_asset_id = :id AND deleted = FALSE
                UNION ALL
                SELECT 1 FROM inventory_report_export WHERE media_asset_id = :id AND deleted = FALSE
                UNION ALL
                SELECT 1 FROM financial_movement_attachment WHERE media_asset_id = :id AND deleted = FALSE AND active = TRUE
            )
            """,
        nativeQuery = true
    )
    boolean isUnreferenced(@Param("id") Long id);

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                  FROM sheet_music_media relation
                  JOIN sheet_music score ON score.id = relation.sheet_music_id AND score.deleted = FALSE
                  JOIN track ON track.id = score.track_id AND track.deleted = FALSE
                 WHERE relation.media_asset_id = :id AND relation.deleted = FALSE
            )
            """,
        nativeQuery = true
    )
    boolean hasActiveSheetMusicReference(@Param("id") Long id);

    @Query(
        value = """
            SELECT asset.*
              FROM media_asset asset
             WHERE asset.id = :id
               AND asset.deleted = FALSE
               AND EXISTS (
                    SELECT 1
                      FROM sheet_music_media relation
                      JOIN sheet_music score ON score.id = relation.sheet_music_id AND score.deleted = FALSE
                      JOIN track ON track.id = score.track_id AND track.deleted = FALSE
                     WHERE relation.media_asset_id = asset.id AND relation.deleted = FALSE
               )
            """,
        nativeQuery = true
    )
    Optional<Media> findActiveSheetMusicMediaById(@Param("id") Long id);
}
