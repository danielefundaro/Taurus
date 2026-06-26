package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.criteria.PreferencesCriteria;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Preferences entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PreferencesRepository extends CommonRepository<Preferences, PreferencesCriteria> {
}
