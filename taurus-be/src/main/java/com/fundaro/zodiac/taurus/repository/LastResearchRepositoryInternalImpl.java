package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.domain.criteria.LastResearchCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.LastResearchRowMapper;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import tech.jhipster.service.ConditionBuilder;

/**
 * Spring Data R2DBC custom repository implementation for the LastResearch entity.
 */
@SuppressWarnings("unused")
class LastResearchRepositoryInternalImpl extends CommonRepositoryInternalImpl<LastResearch, LastResearchCriteria, LastResearchSqlHelper, LastResearchRowMapper> implements LastResearchRepositoryInternal {

    public LastResearchRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        LastResearchRowMapper lastResearchMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new LastResearchSqlHelper(), lastResearchMapper, entityOperations, converter, columnConverter, LastResearch.class, "last_research");
    }

    @Override
    protected Condition buildConditions(LastResearchCriteria criteria, String userId) {
        ConditionBuilder builder = super.commonConditions(criteria, userId);
        if (criteria != null) {
            if (criteria.getValue() != null) {
                builder.buildFilterConditionForField(criteria.getValue(), getEntityTable().column("value"));
            }
            if (criteria.getField() != null) {
                builder.buildFilterConditionForField(criteria.getField(), getEntityTable().column("field"));
            }
        }
        return builder.buildConditions();
    }
}
