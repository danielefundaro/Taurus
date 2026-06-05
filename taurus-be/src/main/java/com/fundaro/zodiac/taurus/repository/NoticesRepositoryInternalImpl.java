package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.NoticesRowMapper;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.jhipster.service.ConditionBuilder;
import tech.jhipster.service.filter.ZonedDateTimeFilter;

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
    public Flux<Notices> findAllUnread(String userId, String tenantCode) {
        NoticesCriteria criteria = new NoticesCriteria();
        ZonedDateTimeFilter readFilter = new ZonedDateTimeFilter();
        readFilter.setSpecified(false);
        criteria.setReadDate(readFilter);

        return createQuery(null, buildConditions(criteria, userId, tenantCode)).all();
    }

    @Override
    public Mono<Long> countUnread(String userId, String tenantCode) {
        NoticesCriteria criteria = new NoticesCriteria();
        ZonedDateTimeFilter readFilter = new ZonedDateTimeFilter();
        readFilter.setSpecified(false);
        criteria.setReadDate(readFilter);

        return createQuery(null, buildConditions(criteria, userId, tenantCode)).all().count();
    }

    @Override
    protected Condition buildConditions(NoticesCriteria criteria, String userId, String tenantCode) {
        ConditionBuilder builder = super.commonConditions(criteria, userId, tenantCode);
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
