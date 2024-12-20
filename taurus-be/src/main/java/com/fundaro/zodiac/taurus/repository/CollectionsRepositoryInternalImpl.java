package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Collections;
import com.fundaro.zodiac.taurus.domain.criteria.CollectionsCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.AlbumsRowMapper;
import com.fundaro.zodiac.taurus.repository.rowmapper.CollectionsRowMapper;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
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
 * Spring Data R2DBC custom repository implementation for the Collections entity.
 */
@SuppressWarnings("unused")
class CollectionsRepositoryInternalImpl extends CommonRepositoryInternalImpl<Collections, CollectionsCriteria, CollectionsSqlHelper, CollectionsRowMapper> implements CollectionsRepositoryInternal {

    private final AlbumsRowMapper albumsMapper;
    private final TracksRowMapper tracksMapper;
    private static final Table albumTable = Table.aliased("albums", "album");
    private static final Table trackTable = Table.aliased("tracks", "track");

    public CollectionsRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        AlbumsRowMapper albumsMapper,
        TracksRowMapper tracksMapper,
        CollectionsRowMapper collectionsMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new CollectionsSqlHelper(), collectionsMapper, entityOperations, converter, columnConverter, Collections.class, "collections");
        this.albumsMapper = albumsMapper;
        this.tracksMapper = tracksMapper;
    }

    @Override
    protected RowsFetchSpec<Collections> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = getSqlHelper().getColumns(getEntityTable(), EntityManager.ENTITY_ALIAS);
        columns.addAll(new AlbumsSqlHelper().getColumns(albumTable, "album"));
        columns.addAll(new PiecesSqlHelper().getColumns(trackTable, "track"));

        SelectFromAndJoinCondition selectFrom = Select.builder()
            .select(columns)
            .from(getEntityTable())
            .leftOuterJoin(albumTable)
            .on(Column.create("album_id", getEntityTable()))
            .equals(Column.create("id", albumTable))
            .leftOuterJoin(trackTable)
            .on(Column.create("track_id", getEntityTable()))
            .equals(Column.create("id", trackTable));
        return super.createQuery(selectFrom, pageable, whereClause);
    }

    @Override
    protected Collections process(Row row, RowMetadata metadata) {
        Collections entity = super.process(row, metadata);
        entity.setAlbum(albumsMapper.apply(row, "album"));
        entity.setTrack(tracksMapper.apply(row, "track"));
        return entity;
    }

    @Override
    protected Condition buildConditions(CollectionsCriteria criteria) {
        ConditionBuilder builder = super.commonConditions(criteria);
        if (criteria != null) {
            if (criteria.getOrderNumber() != null) {
                builder.buildFilterConditionForField(criteria.getOrderNumber(), getEntityTable().column("order_number"));
            }
            if (criteria.getAlbumId() != null) {
                builder.buildFilterConditionForField(criteria.getAlbumId(), albumTable.column("id"));
            }
            if (criteria.getTrackId() != null) {
                builder.buildFilterConditionForField(criteria.getTrackId(), trackTable.column("id"));
            }
        }
        return builder.buildConditions();
    }
}
