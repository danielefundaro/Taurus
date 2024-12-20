package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.domain.criteria.LkTrackTypeCriteria;
import com.fundaro.zodiac.taurus.repository.LkTrackTypeRepository;
import com.fundaro.zodiac.taurus.service.LkTrackTypeService;
import com.fundaro.zodiac.taurus.service.dto.LkTrackTypeDTO;
import com.fundaro.zodiac.taurus.service.mapper.LkTrackTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.LkTrackType}.
 */
@Service
@Transactional
public class LkTrackTypeServiceImpl extends CommonServiceImpl<LkTrackType, LkTrackTypeDTO, LkTrackTypeCriteria, LkTrackTypeMapper, LkTrackTypeRepository> implements LkTrackTypeService {

    public LkTrackTypeServiceImpl(LkTrackTypeRepository lkTrackTypeRepository, LkTrackTypeMapper lkTrackTypeMapper) {
        super(lkTrackTypeRepository, lkTrackTypeMapper, LkTrackTypeService.class, LkTrackType.class.getSimpleName());
    }
}
