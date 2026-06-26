package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.domain.criteria.LastResearchCriteria;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the LastResearch entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LastResearchRepository extends CommonRepository<LastResearch, LastResearchCriteria> {
}
