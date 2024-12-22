package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.domain.criteria.LastResearchCriteria;
import com.fundaro.zodiac.taurus.service.LastResearchService;
import com.fundaro.zodiac.taurus.service.dto.LastResearchDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.LastResearch}.
 */
@RestController
@RequestMapping("/api/last-researches")
public class LastResearchResource extends CommonResource<LastResearch, LastResearchDTO, LastResearchCriteria, LastResearchService> {

    public LastResearchResource(LastResearchService lastResearchService) {
        super(lastResearchService, LastResearchResource.class, LastResearch.class.getSimpleName());
    }
}
