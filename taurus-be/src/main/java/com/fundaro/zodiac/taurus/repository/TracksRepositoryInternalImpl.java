package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.LkTrackTypeRowMapper;
import com.fundaro.zodiac.taurus.repository.rowmapper.TracksRowMapper;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data R2DBC custom repository implementation for the Tracks entity.
 */
@SuppressWarnings("unused")
class TracksRepositoryInternalImpl extends CommonRepositoryInternalImpl<Tracks, TracksCriteria, TracksSqlHelper, TracksRowMapper> implements TracksRepositoryInternal {

    private final LkTrackTypeRowMapper lkTrackTypeMapper;
    private static final Table typeTable = Table.aliased("lk_track_type", "e_type");

    public TracksRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        LkTrackTypeRowMapper lkTrackTypeMapper,
        TracksRowMapper tracksMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new TracksSqlHelper(), tracksMapper, entityOperations, converter, columnConverter, Tracks.class, "tracks");
        this.lkTrackTypeMapper = lkTrackTypeMapper;
    }

    @Override
    protected RowsFetchSpec<Tracks> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = getSqlHelper().getColumns(getEntityTable(), EntityManager.ENTITY_ALIAS);
        columns.addAll(new LkTrackTypeSqlHelper().getColumns(typeTable, "type"));
        SelectFromAndJoinCondition selectFrom = Select.builder()
            .select(columns)
            .from(getEntityTable())
            .leftOuterJoin(typeTable)
            .on(Column.create("type_id", getEntityTable()))
            .equals(Column.create("id", typeTable));
        return super.createQuery(selectFrom, pageable, whereClause);
    }

    @Override
    protected Tracks process(Row row, RowMetadata metadata) {
        Tracks entity = super.process(row, metadata);
        entity.setType(lkTrackTypeMapper.apply(row, "type"));
        return entity;
    }

    @Override
    protected Condition buildConditions(TracksCriteria criteria) {
        ConditionBuilder builder = super.commonConditions(criteria);
        if (criteria != null) {
            if (criteria.getName() != null) {
                builder.buildFilterConditionForField(criteria.getName(), getEntityTable().column("name"));
            }
            if (criteria.getDescription() != null) {
                builder.buildFilterConditionForField(criteria.getDescription(), getEntityTable().column("description"));
            }
            if (criteria.getComposer() != null) {
                builder.buildFilterConditionForField(criteria.getComposer(), getEntityTable().column("composer"));
            }
            if (criteria.getArranger() != null) {
                builder.buildFilterConditionForField(criteria.getArranger(), getEntityTable().column("arranger"));
            }
            if (criteria.getTypeId() != null) {
                builder.buildFilterConditionForField(criteria.getTypeId(), typeTable.column("id"));
            }
        }
        return builder.buildConditions();
    }
}
