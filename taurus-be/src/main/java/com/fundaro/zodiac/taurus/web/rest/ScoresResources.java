package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Scores;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.ScoresService;
import com.fundaro.zodiac.taurus.service.dto.ScoresDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link Scores}.
 */
@RestController
@RequestMapping("/api/scores")
public class ScoresResources extends CommonOpenSearchResource<Scores, ScoresDTO, MediaCriteria, ScoresService> {

    public ScoresResources(ScoresService scoresService) {
        super(scoresService, "Scores", ScoresResources.class);
    }
}
