package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.mapper.InstrumentsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link Instruments}.
 */
@Service
@Transactional
public class InstrumentsServiceImpl extends CommonOpenSearchServiceImpl<Instruments, InstrumentsDTO, InstrumentsCriteria, InstrumentsMapper> implements InstrumentsService {

    public InstrumentsServiceImpl(OpenSearchService openSearchService, InstrumentsMapper instrumentsMapper) {
        super(openSearchService, instrumentsMapper, InstrumentsService.class, Instruments.class, "Instruments");
    }
}
