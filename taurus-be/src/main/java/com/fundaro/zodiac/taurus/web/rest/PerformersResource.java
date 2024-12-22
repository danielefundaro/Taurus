package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Performers;
import com.fundaro.zodiac.taurus.domain.criteria.PerformersCriteria;
import com.fundaro.zodiac.taurus.service.PerformersService;
import com.fundaro.zodiac.taurus.service.dto.PerformersDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Performers}.
 */
@RestController
@RequestMapping("/api/performers")
public class PerformersResource extends CommonResource<Performers, PerformersDTO, PerformersCriteria, PerformersService> {

    public PerformersResource(PerformersService performersService) {
        super(performersService, PerformersResource.class, Performers.class.getSimpleName());
    }
}
