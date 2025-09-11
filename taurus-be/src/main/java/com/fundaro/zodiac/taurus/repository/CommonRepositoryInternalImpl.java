package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import com.fundaro.zodiac.taurus.domain.criteria.CommonCriteria;
import com.fundaro.zodiac.taurus.repository.rowmapper.ColumnConverter;
import com.fundaro.zodiac.taurus.repository.rowmapper.CommonRowMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.SimpleR2dbcRepository;
import org.springframework.data.relational.core.sql.*;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoin;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoinCondition;
import org.springframework.data.relational.repository.support.MappingRelationalEntityInformation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.jhipster.service.ConditionBuilder;
import tech.jhipster.service.filter.StringFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data R2DBC custom repository implementation for the CommonFields entity.
 */
@SuppressWarnings("unused")
public abstract class CommonRepositoryInternalImpl<E extends CommonFields, C extends CommonCriteria, H extends CommonSqlHelper, M extends CommonRowMapper<E>> extends SimpleR2dbcRepository<E, Long> implements CommonRepositoryInternal<E, C> {

    private final DatabaseClient db;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final EntityManager entityManager;

    private final H sqlHelper;
    private final M mapper;
    private final ColumnConverter columnConverter;

    private final Class<E> entityClass;
    private final Table entityTable;

    public CommonRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        H sqlHelper,
        M mapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter,
        ColumnConverter columnConverter,
        Class<E> entityClass,
        String tableName
    ) {
        super(
            new MappingRelationalEntityInformation(converter.getMappingContext().getRequiredPersistentEntity(entityClass)),
            entityOperations,
            converter
        );
        this.db = template.getDatabaseClient();
        this.r2dbcEntityTemplate = template;
        this.entityManager = entityManager;
        this.sqlHelper = sqlHelper;
        this.mapper = mapper;
        this.columnConverter = columnConverter;
        this.entityClass = entityClass;
        this.entityTable = Table.aliased(tableName, EntityManager.ENTITY_ALIAS);
    }

    protected Table getEntityTable() {
        return entityTable;
    }

    protected H getSqlHelper() {
        return sqlHelper;
    }

    protected abstract Condition buildConditions(C criteria, String userId);

    @Override
    public Flux<E> findAll() {
        return findAllBy(null);
    }

    @Override
    public Flux<E> findAllBy(Pageable pageable) {
        return createQuery(pageable, null).all();
    }

    @Override
    public Mono<E> findByIdAndUserId(Long id, String userId) {
        Condition whereClause = Conditions.isEqual(entityTable.column("id"), Conditions.just(id.toString()))
            .and(Conditions.isEqual(entityTable.column("user_id"), Conditions.just(String.format("'%s'", userId))));
        return createQuery(null, whereClause).one();
    }

    @Override
    public <S extends E> Mono<S> save(S entity) {
        return super.save(entity);
    }

    @Override
    public Flux<E> findByCriteria(C criteria, Pageable page, String userId) {
        return createQuery(page, buildConditions(criteria, userId)).all();
    }

    @Override
    public Mono<Long> countByCriteria(C criteria, String userId) {
        return findByCriteria(criteria, null, userId)
            .collectList()
            .map(collectedList -> collectedList != null ? (long) collectedList.size() : (long) 0);
    }

    @Override
    public Mono<Void> deleteByIdAndUserId(Long id, String userId) {
        return findByIdAndUserId(id, userId).map(entity -> {
            entity.setDeleted(true);
            return entity;
        }).flatMap(this::save).then();
    }

    protected ConditionBuilder commonConditions(C criteria, String userId) {
        ConditionBuilder builder = new ConditionBuilder(this.columnConverter);
        List<Condition> allConditions = new ArrayList<Condition>();
        if (criteria != null) {
            if (criteria.getId() != null) {
                builder.buildFilterConditionForField(criteria.getId(), entityTable.column("id"));
            }
        }

        builder.buildFilterConditionForField(new StringFilter().setEquals(userId), entityTable.column("user_id"));
        return builder;
    }

    protected RowsFetchSpec<E> createQuery(SelectFromAndJoinCondition selectFrom, Pageable pageable, Condition whereClause) {
        List<Expression> columns = sqlHelper.getColumns(entityTable, EntityManager.ENTITY_ALIAS);
        // we do not support Criteria here for now as of https://github.com/jhipster/generator-jhipster/issues/18269
        whereClause = applySoftDeleteCondition(whereClause);
        String select = entityManager.createSelect(selectFrom, entityClass, pageable, whereClause);

        return db.sql(select).map(this::process);
    }

    protected RowsFetchSpec<E> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = sqlHelper.getColumns(entityTable, EntityManager.ENTITY_ALIAS);
        SelectFromAndJoin selectFrom = Select.builder().select(columns).from(entityTable);
        // we do not support Criteria here for now as of https://github.com/jhipster/generator-jhipster/issues/18269
        whereClause = applySoftDeleteCondition(whereClause);
        String select = entityManager.createSelect(selectFrom, entityClass, pageable, whereClause);

        return db.sql(select).map(this::process);
    }

    protected E process(Row row, RowMetadata metadata) {
        return mapper.apply(row, "e");
    }

    private Condition applySoftDeleteCondition(Condition whereClause) {
        Condition whereDeletedFalse = Conditions.isEqual(entityTable.column("deleted"), Conditions.just("false"));

        if (whereClause != null) {
            whereDeletedFalse = whereDeletedFalse.and(whereClause);
        }

        return whereDeletedFalse;
    }
}
