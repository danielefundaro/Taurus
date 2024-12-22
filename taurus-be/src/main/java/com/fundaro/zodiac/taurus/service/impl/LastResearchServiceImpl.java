package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.domain.criteria.LastResearchCriteria;
import com.fundaro.zodiac.taurus.repository.LastResearchRepository;
import com.fundaro.zodiac.taurus.service.LastResearchService;
import com.fundaro.zodiac.taurus.service.dto.LastResearchDTO;
import com.fundaro.zodiac.taurus.service.mapper.LastResearchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.LastResearch}.
 */
@Service
@Transactional
public class LastResearchServiceImpl extends CommonServiceImpl<LastResearch, LastResearchDTO, LastResearchCriteria, LastResearchMapper, LastResearchRepository> implements LastResearchService {

    public LastResearchServiceImpl(LastResearchRepository lastResearchRepository, LastResearchMapper lastResearchMapper) {
        super(lastResearchRepository, lastResearchMapper, LastResearchService.class, LastResearch.class.getSimpleName());
    }
}
