package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.NoticesRowMapper;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import tech.jhipster.service.ConditionBuilder;

/**
 * Spring Data R2DBC custom repository implementation for the Notices entity.
 */
@SuppressWarnings("unused")
class NoticesRepositoryInternalImpl extends CommonRepositoryInternalImpl<Notices, NoticesCriteria, NoticesSqlHelper, NoticesRowMapper> implements NoticesRepositoryInternal {

    public NoticesRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        NoticesRowMapper noticesMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new NoticesSqlHelper(), noticesMapper, entityOperations, converter, columnConverter, Notices.class, "notices");
    }

    @Override
    protected Condition buildConditions(NoticesCriteria criteria, String userId) {
        ConditionBuilder builder = super.commonConditions(criteria, userId);
        if (criteria != null) {
            if (criteria.getName() != null) {
                builder.buildFilterConditionForField(criteria.getName(), getEntityTable().column("name"));
            }
            if (criteria.getMessage() != null) {
                builder.buildFilterConditionForField(criteria.getMessage(), getEntityTable().column("message"));
            }
            if (criteria.getReadDate() != null) {
                builder.buildFilterConditionForField(criteria.getReadDate(), getEntityTable().column("read_date"));
            }
        }
        return builder.buildConditions();
    }
}
