package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.mapper.InstrumentsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InstrumentsServiceImpl extends CommonOpenSearchServiceImpl<Instruments, InstrumentsDTO, InstrumentsCriteria, InstrumentsMapper, InstrumentsRepository>
    implements InstrumentsService {

    public InstrumentsServiceImpl(InstrumentsRepository repository, InstrumentsMapper mapper) {
        super(repository, mapper, InstrumentsService.class, Instruments.class);
    }
}
