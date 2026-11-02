package com.fundaro.zodiac.taurus.repository.instrumentsuggestion;

import com.fundaro.zodiac.taurus.domain.instrumentsuggestion.SheetMusicInstrumentSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public interface SheetMusicInstrumentSuggestionRepository extends JpaRepository<SheetMusicInstrumentSuggestion, Long> {

    @Query(
        "select suggestion from SheetMusicInstrumentSuggestion suggestion " +
            "where suggestion.sheetMusicId in :sheetMusicIds and suggestion.deleted = false " +
            "and suggestion.status = com.fundaro.zodiac.taurus.domain.instrumentsuggestion.InstrumentSuggestionStatus.OPEN " +
            "order by suggestion.id"
    )
    List<SheetMusicInstrumentSuggestion> findOpenBySheetMusicIds(@Param("sheetMusicIds") Collection<Long> sheetMusicIds);

    /** The suggestions go with the parts removed from a track. */
    @Modifying(flushAutomatically = true)
    @Query(
        "update SheetMusicInstrumentSuggestion suggestion set suggestion.deleted = true, suggestion.editBy = :actor, " +
            "suggestion.editDate = :now where suggestion.sheetMusicId in :sheetMusicIds and suggestion.deleted = false"
    )
    int deleteBySheetMusicIds(
        @Param("sheetMusicIds") Collection<Long> sheetMusicIds,
        @Param("actor") String actor,
        @Param("now") ZonedDateTime now
    );
}
