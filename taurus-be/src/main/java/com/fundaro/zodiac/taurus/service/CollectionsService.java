package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Collections;
import com.fundaro.zodiac.taurus.domain.criteria.CollectionsCriteria;
import com.fundaro.zodiac.taurus.service.dto.CollectionsDTO;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Collections}.
 */
public interface CollectionsService extends CommonService<Collections, CollectionsDTO, CollectionsCriteria> {
}
