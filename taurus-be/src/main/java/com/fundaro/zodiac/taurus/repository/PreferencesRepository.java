package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.criteria.PreferencesCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Preferences entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PreferencesRepository extends CommonRepository<Preferences, PreferencesCriteria>, PreferencesRepositoryInternal {
}

interface PreferencesRepositoryInternal extends CommonRepositoryInternal<Preferences, PreferencesCriteria> {
}
