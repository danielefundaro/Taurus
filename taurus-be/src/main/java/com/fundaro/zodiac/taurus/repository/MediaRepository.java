package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Media entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MediaRepository extends CommonRepository<Media, MediaCriteria>, MediaRepositoryInternal {
}

interface MediaRepositoryInternal extends CommonRepositoryInternal<Media, MediaCriteria> {
}
