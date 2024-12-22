package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.MediaRowMapper;
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

import java.util.List;

/**
 * Spring Data R2DBC custom repository implementation for the Media entity.
 */
@SuppressWarnings("unused")
class MediaRepositoryInternalImpl extends CommonRepositoryInternalImpl<Media, MediaCriteria, MediaSqlHelper, MediaRowMapper> implements MediaRepositoryInternal {

    private final TracksRowMapper tracksMapper;
    private static final Table trackTable = Table.aliased("tracks", "track");

    public MediaRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        TracksRowMapper tracksMapper,
        MediaRowMapper mediaMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new MediaSqlHelper(), mediaMapper, entityOperations, converter, columnConverter, Media.class, "media");
        this.tracksMapper = tracksMapper;
    }

    @Override
    protected RowsFetchSpec<Media> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = getSqlHelper().getColumns(getEntityTable(), EntityManager.ENTITY_ALIAS);
        columns.addAll(new TracksSqlHelper().getColumns(trackTable, "track"));
        SelectFromAndJoinCondition selectFrom = Select.builder()
            .select(columns)
            .from(getEntityTable())
            .leftOuterJoin(trackTable)
            .on(Column.create("track_id", getEntityTable()))
            .equals(Column.create("id", trackTable));
        return super.createQuery(selectFrom, pageable, whereClause);
    }

    @Override
    protected Media process(Row row, RowMetadata metadata) {
        Media entity = super.process(row, metadata);
        entity.setTrack(tracksMapper.apply(row, "track"));
        return entity;
    }

    @Override
    protected Condition buildConditions(MediaCriteria criteria) {
        ConditionBuilder builder = super.commonConditions(criteria);
        if (criteria != null) {
            if (criteria.getName() != null) {
                builder.buildFilterConditionForField(criteria.getName(), getEntityTable().column("name"));
            }
            if (criteria.getDescription() != null) {
                builder.buildFilterConditionForField(criteria.getDescription(), getEntityTable().column("description"));
            }
            if (criteria.getOrderNumber() != null) {
                builder.buildFilterConditionForField(criteria.getOrderNumber(), getEntityTable().column("order_number"));
            }
            if (criteria.getTrackId() != null) {
                builder.buildFilterConditionForField(criteria.getTrackId(), trackTable.column("id"));
            }
        }
        return builder.buildConditions();
    }
}
