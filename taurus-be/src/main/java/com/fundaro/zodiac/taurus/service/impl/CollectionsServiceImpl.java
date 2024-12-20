package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Collections;
import com.fundaro.zodiac.taurus.domain.criteria.CollectionsCriteria;
import com.fundaro.zodiac.taurus.repository.CollectionsRepository;
import com.fundaro.zodiac.taurus.service.CollectionsService;
import com.fundaro.zodiac.taurus.service.dto.CollectionsDTO;
import com.fundaro.zodiac.taurus.service.mapper.CollectionsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Collections}.
 */
@Service
@Transactional
public class CollectionsServiceImpl extends CommonServiceImpl<Collections, CollectionsDTO, CollectionsCriteria, CollectionsMapper, CollectionsRepository> implements CollectionsService {

    public CollectionsServiceImpl(CollectionsRepository collectionsRepository, CollectionsMapper collectionsMapper) {
        super(collectionsRepository, collectionsMapper, CollectionsService.class, Collections.class.getSimpleName());
    }
}
