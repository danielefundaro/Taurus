package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Scores;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.dto.ScoresDTO;


/**
 * Service Interface for managing {@link Scores}.
 */
public interface ScoresService extends CommonOpenSearchService<Scores, ScoresDTO, MediaCriteria> {
}
