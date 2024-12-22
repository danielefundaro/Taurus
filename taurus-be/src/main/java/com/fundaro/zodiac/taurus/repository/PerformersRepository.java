package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Performers;
import com.fundaro.zodiac.taurus.domain.criteria.PerformersCriteria;
import org.springframework.stereotype.Repository;

/**
 * Spring Data R2DBC repository for the Performers entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PerformersRepository extends CommonRepository<Performers, PerformersCriteria>, PerformersRepositoryInternal {
}

interface PerformersRepositoryInternal extends CommonRepositoryInternal<Performers, PerformersCriteria> {
}
