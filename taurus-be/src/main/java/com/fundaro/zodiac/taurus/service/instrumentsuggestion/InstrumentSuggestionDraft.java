package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import com.fundaro.zodiac.taurus.domain.instrumentsuggestion.InstrumentSuggestionSource;

/** A name to add found on a part being created from a PDF; saved with the part, once it has its id. */
public record InstrumentSuggestionDraft(
    String readText,
    String proposedName,
    String normalizedName,
    String catalogKeyword,
    InstrumentSuggestionSource source
) {}
