package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.domain.criteria.LkTrackTypeCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the LkTrackType entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LkTrackTypeRepository extends CommonRepository<LkTrackType, LkTrackTypeCriteria>, LkTrackTypeRepositoryInternal {
}

interface LkTrackTypeRepositoryInternal extends CommonRepositoryInternal<LkTrackType, LkTrackTypeCriteria> {
}
