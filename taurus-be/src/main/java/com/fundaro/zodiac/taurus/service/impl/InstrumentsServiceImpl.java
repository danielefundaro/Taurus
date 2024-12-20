package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.mapper.InstrumentsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Instruments}.
 */
@Service
@Transactional
public class InstrumentsServiceImpl extends CommonServiceImpl<Instruments, InstrumentsDTO, InstrumentsCriteria, InstrumentsMapper, InstrumentsRepository> implements InstrumentsService {

    public InstrumentsServiceImpl(InstrumentsRepository instrumentsRepository, InstrumentsMapper instrumentsMapper) {
        super(instrumentsRepository, instrumentsMapper, InstrumentsService.class, Instruments.class.getSimpleName());
    }
}
