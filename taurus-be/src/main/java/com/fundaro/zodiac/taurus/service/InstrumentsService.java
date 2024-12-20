package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Instruments}.
 */
public interface InstrumentsService extends CommonService<Instruments, InstrumentsDTO, InstrumentsCriteria> {
}
