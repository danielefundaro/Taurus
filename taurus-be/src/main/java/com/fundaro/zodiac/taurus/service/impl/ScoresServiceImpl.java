package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Scores;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.ScoresService;
import com.fundaro.zodiac.taurus.service.dto.ScoresDTO;
import com.fundaro.zodiac.taurus.service.mapper.ScoresMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link Scores}.
 */
@Service
@Transactional
public class ScoresServiceImpl extends CommonOpenSearchServiceImpl<Scores, ScoresDTO, MediaCriteria, ScoresMapper> implements ScoresService {

    public ScoresServiceImpl(OpenSearchService openSearchService, ScoresMapper scoresMapper) {
        super(openSearchService, scoresMapper, ScoresService.class, Scores.class, "Scores");
    }
}
