package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.domain.criteria.LkTrackTypeCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.LkTrackTypeRowMapper;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import tech.jhipster.service.ConditionBuilder;

/**
 * Spring Data R2DBC custom repository implementation for the LkTrackType entity.
 */
@SuppressWarnings("unused")
class LkTrackTypeRepositoryInternalImpl extends CommonRepositoryInternalImpl<LkTrackType, LkTrackTypeCriteria, LkTrackTypeSqlHelper, LkTrackTypeRowMapper> implements LkTrackTypeRepositoryInternal {

    public LkTrackTypeRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        LkTrackTypeRowMapper lkTrackTypeMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new LkTrackTypeSqlHelper(), lkTrackTypeMapper, entityOperations, converter, columnConverter, LkTrackType.class, "lk_track_type");
    }

    @Override
    protected Condition buildConditions(LkTrackTypeCriteria criteria) {
        ConditionBuilder builder = super.commonConditions(criteria);
        if (criteria != null) {
            if (criteria.getName() != null) {
                builder.buildFilterConditionForField(criteria.getName(), getEntityTable().column("name"));
            }
            if (criteria.getDescription() != null) {
                builder.buildFilterConditionForField(criteria.getDescription(), getEntityTable().column("description"));
            }
        }
        return builder.buildConditions();
    }
}
