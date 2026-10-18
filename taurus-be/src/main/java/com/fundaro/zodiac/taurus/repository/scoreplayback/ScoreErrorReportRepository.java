package com.fundaro.zodiac.taurus.repository.scoreplayback;

import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreErrorReport;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreErrorReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScoreErrorReportRepository extends JpaRepository<ScoreErrorReport, Long> {

    Optional<ScoreErrorReport> findByIdAndTrackIdAndDeletedFalse(Long id, Long trackId);

    List<ScoreErrorReport> findAllBySheetMusicIdInAndDeletedFalseOrderByIdDesc(Collection<Long> sheetMusicIds);

    List<ScoreErrorReport> findAllBySheetMusicIdAndStatusAndDeletedFalse(Long sheetMusicId, ScoreErrorReportStatus status);

    long countBySheetMusicIdAndReportedByAndStatusAndDeletedFalse(Long sheetMusicId, String reportedBy, ScoreErrorReportStatus status);

    /** Open reports of each of the given parts: one row {@code [sheetMusicId, count]} per part with at least one. */
    @Query(
        "select report.sheetMusicId, count(report) from ScoreErrorReport report " +
            "where report.sheetMusicId in :sheetMusicIds and report.deleted = false " +
            "and report.status = com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreErrorReportStatus.OPEN " +
            "group by report.sheetMusicId"
    )
    List<Object[]> countOpenBySheetMusicIds(@Param("sheetMusicIds") Collection<Long> sheetMusicIds);

    /** Physical deletion of the reports written by a user, for the erasure of their data. */
    @Modifying
    @Query("delete from ScoreErrorReport report where report.reportedBy = :reportedBy")
    int deleteAllByReportedBy(@Param("reportedBy") String reportedBy);
}
