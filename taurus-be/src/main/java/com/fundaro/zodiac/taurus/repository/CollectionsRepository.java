package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Collections;
import com.fundaro.zodiac.taurus.domain.criteria.CollectionsCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Collections entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CollectionsRepository extends CommonRepository<Collections, CollectionsCriteria>, CollectionsRepositoryInternal {
}

interface CollectionsRepositoryInternal extends CommonRepositoryInternal<Collections, CollectionsCriteria> {
}
