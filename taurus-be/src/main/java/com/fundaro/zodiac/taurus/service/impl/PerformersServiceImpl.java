package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Performers;
import com.fundaro.zodiac.taurus.domain.criteria.PerformersCriteria;
import com.fundaro.zodiac.taurus.repository.PerformersRepository;
import com.fundaro.zodiac.taurus.service.PerformersService;
import com.fundaro.zodiac.taurus.service.dto.PerformersDTO;
import com.fundaro.zodiac.taurus.service.mapper.PerformersMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Performers}.
 */
@Service
@Transactional
public class PerformersServiceImpl extends CommonServiceImpl<Performers, PerformersDTO, PerformersCriteria, PerformersMapper, PerformersRepository> implements PerformersService {

    public PerformersServiceImpl(PerformersRepository performersRepository, PerformersMapper performersMapper) {
        super(performersRepository, performersMapper, PerformersService.class, Performers.class.getSimpleName());
    }
}
