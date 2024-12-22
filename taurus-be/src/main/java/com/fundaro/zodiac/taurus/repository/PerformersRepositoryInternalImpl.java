package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Performers;
import com.fundaro.zodiac.taurus.domain.criteria.PerformersCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.InstrumentsRowMapper;
import com.fundaro.zodiac.taurus.repository.rowmapper.MediaRowMapper;
import com.fundaro.zodiac.taurus.repository.rowmapper.PerformersRowMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.*;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoinCondition;
import org.springframework.r2dbc.core.RowsFetchSpec;
import tech.jhipster.service.ConditionBuilder;

import java.util.List;

/**
 * Spring Data R2DBC custom repository implementation for the Performers entity.
 */
@SuppressWarnings("unused")
class PerformersRepositoryInternalImpl extends CommonRepositoryInternalImpl<Performers, PerformersCriteria, PerformersSqlHelper, PerformersRowMapper> implements PerformersRepositoryInternal {

    private final InstrumentsRowMapper instrumentsMapper;
    private final MediaRowMapper mediaMapper;
    private static final Table instrumentTable = Table.aliased("instruments", "instrument");
    private static final Table mediaTable = Table.aliased("media", "media");

    public PerformersRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        InstrumentsRowMapper instrumentsMapper,
        MediaRowMapper mediaMapper,
        PerformersRowMapper performersMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new PerformersSqlHelper(), performersMapper, entityOperations, converter, columnConverter, Performers.class, "performers");
        this.instrumentsMapper = instrumentsMapper;
        this.mediaMapper = mediaMapper;
    }

    @Override
    protected RowsFetchSpec<Performers> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = getSqlHelper().getColumns(getEntityTable(), EntityManager.ENTITY_ALIAS);
        columns.addAll(new InstrumentsSqlHelper().getColumns(instrumentTable, "instrument"));
        columns.addAll(new MediaSqlHelper().getColumns(mediaTable, "media"));
        SelectFromAndJoinCondition selectFrom = Select.builder()
            .select(columns)
            .from(getEntityTable())
            .leftOuterJoin(instrumentTable)
            .on(Column.create("instrument_id", getEntityTable()))
            .equals(Column.create("id", instrumentTable))
            .leftOuterJoin(mediaTable)
            .on(Column.create("media_id", getEntityTable()))
            .equals(Column.create("id", mediaTable));
        return super.createQuery(selectFrom, pageable, whereClause);
    }

    @Override
    protected Performers process(Row row, RowMetadata metadata) {
        Performers entity = super.process(row, metadata);
        entity.setInstrument(instrumentsMapper.apply(row, "instrument"));
        entity.setMedia(mediaMapper.apply(row, "media"));
        return entity;
    }

    @Override
    protected Condition buildConditions(PerformersCriteria criteria) {
        ConditionBuilder builder = super.commonConditions(criteria);
        if (criteria != null) {
            if (criteria.getDescription() != null) {
                builder.buildFilterConditionForField(criteria.getDescription(), getEntityTable().column("description"));
            }
            if (criteria.getInstrumentId() != null) {
                builder.buildFilterConditionForField(criteria.getInstrumentId(), instrumentTable.column("id"));
            }
            if (criteria.getMediaId() != null) {
                builder.buildFilterConditionForField(criteria.getMediaId(), mediaTable.column("id"));
            }
        }
        return builder.buildConditions();
    }
}
