package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.AlbumsRowMapper;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.SimpleR2dbcRepository;
import org.springframework.data.relational.core.sql.Comparison;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoin;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.repository.support.MappingRelationalEntityInformation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.jhipster.service.ConditionBuilder;

/**
 * Spring Data R2DBC custom repository implementation for the Albums entity.
 */
@SuppressWarnings("unused")
class AlbumsRepositoryInternalImpl extends CommonRepositoryInternalImpl<Albums, AlbumsCriteria, AlbumsSqlHelper, AlbumsRowMapper> implements AlbumsRepositoryInternal {

    public AlbumsRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        AlbumsRowMapper albumsMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter
    ) {
        super(template, entityManager, new AlbumsSqlHelper(), albumsMapper, entityOperations, converter, columnConverter, Albums.class, "albums");
    }

    @Override
    protected Condition buildConditions(AlbumsCriteria criteria) {
        ConditionBuilder builder = super.commonConditions(criteria);
        if (criteria != null) {
            if (criteria.getName() != null) {
                builder.buildFilterConditionForField(criteria.getName(), getEntityTable().column("name"));
            }
            if (criteria.getDescription() != null) {
                builder.buildFilterConditionForField(criteria.getDescription(), getEntityTable().column("description"));
            }
            if (criteria.getDate() != null) {
                builder.buildFilterConditionForField(criteria.getDate(), getEntityTable().column("date"));
            }
        }
        return builder.buildConditions();
    }
}
