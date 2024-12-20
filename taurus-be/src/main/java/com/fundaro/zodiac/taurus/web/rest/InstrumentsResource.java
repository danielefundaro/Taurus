package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Instruments}.
 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentsResource extends CommonResource<Instruments, InstrumentsDTO, InstrumentsCriteria, InstrumentsService> {

    public InstrumentsResource(InstrumentsService instrumentsService) {
        super(instrumentsService, InstrumentsResource.class, Instruments.class.getSimpleName());
    }
}
