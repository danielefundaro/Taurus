package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Instruments entity.
 */
@SuppressWarnings("unused")
@Repository
public interface InstrumentsRepository extends CommonRepository<Instruments, InstrumentsCriteria>, InstrumentsRepositoryInternal {
}

interface InstrumentsRepositoryInternal extends CommonRepositoryInternal<Instruments, InstrumentsCriteria> {
}
