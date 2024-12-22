package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.domain.criteria.LastResearchCriteria;
import org.springframework.stereotype.Repository;

/**
 * Spring Data R2DBC repository for the LastResearch entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LastResearchRepository extends CommonRepository<LastResearch, LastResearchCriteria>, LastResearchRepositoryInternal {
}

interface LastResearchRepositoryInternal extends CommonRepositoryInternal<LastResearch, LastResearchCriteria> {
}
