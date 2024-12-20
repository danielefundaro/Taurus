package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.InstrumentsRowMapper;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.Condition;
import tech.jhipster.service.ConditionBuilder;

/**
 * Spring Data R2DBC custom repository implementation for the Instruments entity.
 */
@SuppressWarnings("unused")
class InstrumentsRepositoryInternalImpl extends CommonRepositoryInternalImpl<Instruments, InstrumentsCriteria, InstrumentsSqlHelper, InstrumentsRowMapper> implements InstrumentsRepositoryInternal {

    public InstrumentsRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        InstrumentsRowMapper instrumentsMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new InstrumentsSqlHelper(), instrumentsMapper, entityOperations, converter, columnConverter, Instruments.class, "instruments");
    }

    @Override
    protected Condition buildConditions(InstrumentsCriteria criteria) {
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
