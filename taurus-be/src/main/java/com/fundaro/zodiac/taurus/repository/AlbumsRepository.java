package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Albums entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AlbumsRepository extends CommonRepository<Albums, AlbumsCriteria>, AlbumsRepositoryInternal{
}

interface AlbumsRepositoryInternal extends CommonRepositoryInternal<Albums, AlbumsCriteria> {
}
