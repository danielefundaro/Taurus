package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Tracks entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TracksRepository extends CommonRepository<Tracks, TracksCriteria>, TracksRepositoryInternal {
}

interface TracksRepositoryInternal extends CommonRepositoryInternal<Tracks, TracksCriteria> {
}
