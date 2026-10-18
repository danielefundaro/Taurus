package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.InstrumentPlaybackSuggester;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link Instruments}.
 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentsResource extends CommonOpenSearchResource<Instruments, InstrumentsDTO, InstrumentsCriteria, InstrumentsService> {

    public InstrumentsResource(InstrumentsService instrumentsService) {
        super(instrumentsService, Instruments.class.getSimpleName(), InstrumentsResource.class);
    }

    /**
     * {@code GET /playback-suggestion?name=} : playback settings proposed from an instrument name.
     *
     * @return {@code 200} with the proposal, or {@code 204} when the name does not match the reference catalogue.
     */
    @GetMapping("/playback-suggestion")
    @RequiresTenantFeature(TenantFeature.SCORE_PLAYBACK)
    public ResponseEntity<InstrumentPlaybackSuggester.Suggestion> playbackSuggestion(@RequestParam("name") String name) {
        return InstrumentPlaybackSuggester.suggest(name)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
