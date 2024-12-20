package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.domain.criteria.PiecesCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.MediaRowMapper;
import com.fundaro.zodiac.taurus.repository.rowmapper.PiecesRowMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.SimpleR2dbcRepository;
import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Comparison;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoinCondition;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.repository.support.MappingRelationalEntityInformation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.jhipster.service.ConditionBuilder;

/**
 * Spring Data R2DBC custom repository implementation for the Pieces entity.
 */
@SuppressWarnings("unused")
class PiecesRepositoryInternalImpl extends CommonRepositoryInternalImpl<Pieces, PiecesCriteria, PiecesSqlHelper, PiecesRowMapper> implements PiecesRepositoryInternal {

    private final MediaRowMapper mediaMapper;
    private static final Table mediaTable = Table.aliased("media", "media");

    public PiecesRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        MediaRowMapper mediaMapper,
        PiecesRowMapper piecesMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new PiecesSqlHelper(), piecesMapper, entityOperations, converter, columnConverter, Pieces.class, "pieces");
        this.mediaMapper = mediaMapper;
    }

    @Override
    protected RowsFetchSpec<Pieces> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = getSqlHelper().getColumns(getEntityTable(), EntityManager.ENTITY_ALIAS);
        columns.addAll(new MediaSqlHelper().getColumns(mediaTable, "media"));
        SelectFromAndJoinCondition selectFrom = Select.builder()
            .select(columns)
            .from(getEntityTable())
            .leftOuterJoin(mediaTable)
            .on(Column.create("media_id", getEntityTable()))
            .equals(Column.create("id", mediaTable));
        return super.createQuery(selectFrom, pageable, whereClause);
    }

    @Override
    protected Pieces process(Row row, RowMetadata metadata) {
        Pieces entity = super.process(row, metadata);
        entity.setMedia(mediaMapper.apply(row, "media"));
        return entity;
    }

    @Override
    protected Condition buildConditions(PiecesCriteria criteria) {
        ConditionBuilder builder = super.commonConditions(criteria);
        if (criteria != null) {
            if (criteria.getName() != null) {
                builder.buildFilterConditionForField(criteria.getName(), getEntityTable().column("name"));
            }
            if (criteria.getDescription() != null) {
                builder.buildFilterConditionForField(criteria.getDescription(), getEntityTable().column("description"));
            }
            if (criteria.getType() != null) {
                builder.buildFilterConditionForField(criteria.getType(), getEntityTable().column("type"));
            }
            if (criteria.getContentType() != null) {
                builder.buildFilterConditionForField(criteria.getContentType(), getEntityTable().column("content_type"));
            }
            if (criteria.getPath() != null) {
                builder.buildFilterConditionForField(criteria.getPath(), getEntityTable().column("path"));
            }
            if (criteria.getOrderNumber() != null) {
                builder.buildFilterConditionForField(criteria.getOrderNumber(), getEntityTable().column("order_number"));
            }
            if (criteria.getMediaId() != null) {
                builder.buildFilterConditionForField(criteria.getMediaId(), mediaTable.column("id"));
            }
        }
        return builder.buildConditions();
    }
}
