package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.domain.criteria.PiecesCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Pieces entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PiecesRepository extends CommonRepository<Pieces, PiecesCriteria>, PiecesRepositoryInternal {
}

interface PiecesRepositoryInternal extends CommonRepositoryInternal<Pieces, PiecesCriteria> {
}
