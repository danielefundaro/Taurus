package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentSuggestionDtos.ApplyRequestDTO;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentSuggestionDtos.SuggestionsDTO;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.TrackInstrumentSuggestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The instruments to add read on the parts of a track. Under {@code /api/tracks/**}: for the roles that manage tracks
 * and instruments.
 */
@RestController
@RequestMapping("/api/tracks/{trackId}/instrument-suggestions")
public class TrackInstrumentSuggestionResource {

    private final TrackInstrumentSuggestionService service;

    public TrackInstrumentSuggestionResource(TrackInstrumentSuggestionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SuggestionsDTO> find(@PathVariable Long trackId) {
        return ResponseEntity.ok(service.find(trackId));
    }

    @PostMapping("/apply")
    public ResponseEntity<TracksDTO> apply(
        @PathVariable Long trackId,
        @Valid @RequestBody ApplyRequestDTO request,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(service.apply(trackId, request, token));
    }
}
