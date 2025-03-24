package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;

/**
 * Service Interface for managing {@link Instruments}.
 */
public interface InstrumentsService extends CommonOpenSearchService<Instruments, InstrumentsDTO, InstrumentsCriteria> {
}
