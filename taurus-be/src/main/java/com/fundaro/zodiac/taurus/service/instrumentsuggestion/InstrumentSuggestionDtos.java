package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fundaro.zodiac.taurus.domain.instrumentsuggestion.InstrumentSuggestionSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Requests and responses of the names to add of a track. */
public final class InstrumentSuggestionDtos {

    private InstrumentSuggestionDtos() {}

    public enum Action {
        CREATE,
        ASSOCIATE,
        IGNORE
    }

    /** The open names of a track, one per name, and the version of the track they were read with. */
    public record SuggestionsDTO(Long trackVersion, List<NameDTO> names) {}

    /**
     * A name found on one or more parts.
     *
     * @param action     {@code CREATE} when no tenant instrument matches, {@code ASSOCIATE} when some do
     * @param candidates the tenant instruments the name could be, for {@code ASSOCIATE}
     */
    public record NameDTO(
        String normalizedName,
        String proposedName,
        String catalogKeyword,
        Action action,
        List<CandidateDTO> candidates,
        List<PartDTO> parts
    ) {}

    /** A tenant instrument and how many users play it. */
    public record CandidateDTO(Long id, String name, long usersCount) {}

    /**
     * A part that reports the name.
     *
     * @param hasInstruments the part already has instruments: it is not preselected
     * @param possibleScore  more than four names on the part: probably the full score, not preselected
     */
    public record PartDTO(Long sheetMusicId, String readText, InstrumentSuggestionSource source, boolean hasInstruments, boolean possibleScore) {}

    public record ApplyRequestDTO(@NotNull Long trackVersion, @NotNull @Size(max = 200) List<@NotNull @Valid DecisionDTO> decisions) {}

    /**
     * The decision on a name: create the instrument {@code name}, associate the instrument {@code instrumentId} or ignore
     * the name. The selected parts receive the instrument; the other parts of the name ignore it.
     */
    public record DecisionDTO(
        @NotBlank @Size(max = InstrumentNames.MAX_LENGTH) String normalizedName,
        @NotNull Action action,
        @Size(max = InstrumentNames.MAX_LENGTH) String name,
        Long instrumentId,
        @Size(max = 1000) List<@NotNull Long> sheetMusicIds
    ) {
        /** A name to create, an instrument to associate. */
        @JsonIgnore
        @AssertTrue(message = "A name to create needs the name, a name to associate needs the instrument")
        public boolean isComplete() {
            if (action == Action.CREATE) return name != null && !name.isBlank();
            if (action == Action.ASSOCIATE) return instrumentId != null;
            return true;
        }
    }
}
