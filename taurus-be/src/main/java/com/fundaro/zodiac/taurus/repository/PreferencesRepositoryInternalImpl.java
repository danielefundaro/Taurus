package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.criteria.PreferencesCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.PreferencesRowMapper;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import tech.jhipster.service.ConditionBuilder;

/**
 * Spring Data R2DBC custom repository implementation for the Preferences entity.
 */
@SuppressWarnings("unused")
class PreferencesRepositoryInternalImpl extends CommonRepositoryInternalImpl<Preferences, PreferencesCriteria, PreferencesSqlHelper, PreferencesRowMapper> implements PreferencesRepositoryInternal {

    public PreferencesRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        PreferencesRowMapper preferencesMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new PreferencesSqlHelper(), preferencesMapper, entityOperations, converter, columnConverter, Preferences.class, "preferences");
    }

    @Override
    protected Condition buildConditions(PreferencesCriteria criteria, String userId) {
        ConditionBuilder builder = super.commonConditions(criteria, userId);
        if (criteria != null) {
            if (criteria.getUserId() != null) {
                builder.buildFilterConditionForField(criteria.getUserId(), getEntityTable().column("user_id"));
            }
            if (criteria.getKey() != null) {
                builder.buildFilterConditionForField(criteria.getKey(), getEntityTable().column("key"));
            }
            if (criteria.getValue() != null) {
                builder.buildFilterConditionForField(criteria.getValue(), getEntityTable().column("value"));
            }
        }
        return builder.buildConditions();
    }
}
