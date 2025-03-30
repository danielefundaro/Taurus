package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CommonOpenSearchService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import com.fundaro.zodiac.taurus.service.mapper.EntityOpenSearchMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;
import tech.jhipster.service.filter.StringFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CommonOpenSearchServiceImpl<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria, M extends EntityOpenSearchMapper<D, E>> implements CommonOpenSearchService<E, D, C> {

    private final OpenSearchService openSearchService;

    private final Logger log;

    private final M mapper;

    private final Class<E> classEntity;

    private final String entityName;

    public <T extends CommonOpenSearchService<E, D, C>> CommonOpenSearchServiceImpl(OpenSearchService openSearchService, M mapper, Class<T> logClass, Class<E> classEntity, String entityName) {
        this.openSearchService = openSearchService;
        this.mapper = mapper;
        this.log = LoggerFactory.getLogger(logClass);
        this.classEntity = classEntity;
        this.entityName = entityName;
    }

    public String getEntityName() {
        return entityName;
    }

    public M getMapper() {
        return mapper;
    }

    @Override
    public Mono<D> save(D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to save {} : {}", entityName, dto);

        if (dto.getId() != null) {
            return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("A new %s cannot already have an ID", entityName), entityName, "id.exists"));
        }

        try {
            return Mono.just(mapper.toDto(saveEntity(null, mapper.toEntity(dto), abstractAuthenticationToken)));
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error occurred while saving %s info.\n%s", entityName, e.getMessage()), entityName, "generic"));
        }
    }

    @Override
    public Mono<D> update(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to update {} : {}", entityName, dto);
        return updateDto(id, dto, abstractAuthenticationToken);
    }

    @Override
    public Mono<D> partialUpdate(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to partially update {} : {}", entityName, dto);
        return updateDto(id, dto, abstractAuthenticationToken);
    }

    @Override
    public Mono<Page<D>> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to get all {} by Criteria", entityName);
        List<Query> queries = getQueries(criteria);

        try {
            SearchResponse<E> searchResponse = openSearchService.search(searchRequest -> searchRequest
                .index(getIndex(entityName))
                .from(pageable.getPageNumber() * pageable.getPageSize())
                .size(pageable.getPageSize())
                .trackTotalHits(t -> t.enabled(true))
                .query(q -> q.bool(b -> b.must(queries)))
                .sort(pageable.getSort().get().map(sort -> SortOptions.of(fn -> fn.field(fs -> fs.field(sort.getProperty()).order(sort.isAscending() ? SortOrder.Asc : SortOrder.Desc)))).toList()), classEntity);

            if (searchResponse == null || searchResponse.hits().hits().isEmpty()) {
                return Mono.just(new PageImpl<>(new ArrayList<>(), pageable, 0L));
            }

            List<D> list = searchResponse.hits().hits().stream().map(hit -> {
                D dto = mapper.toDto(hit.source());
                dto.setId(hit.id());
                return dto;
            }).toList();

            return Mono.just(new PageImpl<>(list, pageable, searchResponse.hits().total().value()));
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error occurred while getting information of %s: %s", entityName, e.getMessage()), entityName, "generic"));
        }
    }

    @Override
    public Mono<D> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to get {} : {}", entityName, id);

        try {
            return Mono.just(mapper.toDto(getById(id)));
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound"));
        }
    }

    @Override
    public Mono<Boolean> delete(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to delete {} : {}", entityName, id);
        E entity;

        try {
            entity = getById(id);
            entity.setDeleted(true);
            saveEntity(id, entity, abstractAuthenticationToken);
        } catch (IOException e) {
            return Mono.just(false);
        }

        return Mono.just(entity.getDeleted());
    }

    @Override
    public void alignChildrenInformation(String childId, AbstractAuthenticationToken abstractAuthenticationToken, Function<StringFilter, C> criteriaFunction, BiFunction<D, String, Boolean> function) {
        // Create filter
        StringFilter stringFilter = new StringFilter();
        stringFilter.setEquals(childId);

        // Create criteria
        C c = criteriaFunction.apply(stringFilter);
        Pageable pageable = Pageable.ofSize(20);

        // Search from parent and update children with the same id
        findByCriteria(c, pageable, abstractAuthenticationToken).map(dPage -> {
            dPage.getContent().forEach(dto -> {
                if (function.apply(dto, childId)) {
                    partialUpdate(dto.getId(), dto, abstractAuthenticationToken).then();
                }
            });

            return dPage;
        }).then();
    }

    protected E getById(String id) throws IOException {
        GetResponse<E> getResponse = openSearchService.get(builder -> builder.index(getIndex(entityName)).id(id), classEntity);
        E entity = getResponse.source();

        if (entity != null && Objects.equals(entity.getDeleted(), Boolean.FALSE)) {
            entity.setId(id);
            return entity;
        }

        throw new IOException();
    }

    protected List<Query> getQueries(C criteria) {
        List<Query> queries = new ArrayList<>();
        queries.add(Query.of(f -> f.exists(e -> e.field("deleted"))));
        queries.add(Query.of(f -> f.match(m -> m.field("deleted").query(value -> value.booleanValue(false)))));
        queries.addAll(Converter.stringFilterToQuery("_id", criteria.getId()));
        queries.addAll(Converter.stringFilterToQuery("name", criteria.getName()));
        queries.addAll(Converter.stringFilterToQuery("description", criteria.getName()));

        return queries;
    }

    @NonNull
    private Mono<D> updateDto(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (dto.getId() == null) {
            return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid id", entityName, "id.null"));
        }

        if (!Objects.equals(id, dto.getId())) {
            return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid ID", entityName, "id.invalid"));
        }

        E existingEntity;
        try {
            existingEntity = getById(id);
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound"));
        }

        mapper.partialUpdate(existingEntity, dto);

        try {
            return Mono.just(mapper.toDto(saveEntity(id, existingEntity, abstractAuthenticationToken)));
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error occurred while update %s info.\n%s", entityName, e.getMessage()), entityName, "generic"));
        }
    }

    private E saveEntity(String id, E entity, AbstractAuthenticationToken abstractAuthenticationToken) throws IOException {
        addAuditInfo(entity, abstractAuthenticationToken);
        IndexRequest<E> indexRequest = new IndexRequest.Builder<E>().index(getIndex(entityName)).document(entity).id(id).build();
        IndexResponse indexResponse = openSearchService.index(indexRequest);

        if (indexResponse.result() == Result.Created) {
            entity.setId(indexResponse.id());
        }

        return entity;
    }

    private String getIndex(String indexName) {
//        String tenant = "tenant1";
//        return String.format("%s_%s", tenant, indexName.toLowerCase());
        return Converter.camelCaseToKebabCase(indexName);
    }

    private void addAuditInfo(E entity, AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);

        if (Strings.isNotBlank(userId)) {
            Date now = new Date();
            entity.setEditBy(userId);
            entity.setEditDate(now);

            if (Strings.isBlank(entity.getInsertBy())) {
                entity.setInsertBy(userId);
                entity.setInsertDate(now);
            }

            if (entity.getDeleted() == null) {
                entity.setDeleted(false);
            }
        }
    }
}
