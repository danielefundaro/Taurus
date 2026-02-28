package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import com.fundaro.zodiac.taurus.service.mapper.EntityOpenSearchMapper;
import com.fundaro.zodiac.taurus.service.user.CommonOpenSearchService;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CommonOpenSearchServiceImpl<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria, M extends EntityOpenSearchMapper<D, E>> implements CommonOpenSearchService<E, D, C> {

    private final OpenSearchService openSearchService;

    private final Logger log;

    private final M mapper;

    private final Class<E> classEntity;

    private final String entityName;

    public <T extends CommonOpenSearchService<E, D, C>> CommonOpenSearchServiceImpl(OpenSearchService openSearchService, M mapper, Class<T> logClass, Class<E> classEntity) {
        this.openSearchService = openSearchService;
        this.mapper = mapper;
        this.log = LoggerFactory.getLogger(logClass);
        this.classEntity = classEntity;
        this.entityName = classEntity.getSimpleName();
    }

    public String getEntityName() {
        return entityName;
    }

    public M getMapper() {
        return mapper;
    }

    public Logger getLogger() {
        return log;
    }

    @Override
    public Mono<Page<D>> findEntitiesByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to get all {} by Criteria", entityName);

        try {
            return Mono.just(findByCriteria(criteria, pageable, abstractAuthenticationToken));
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error occurred while getting information of %s: %s", entityName, e.getMessage()), entityName, "generic"));
        }
    }

    @Override
    public Mono<D> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to get {} : {}", entityName, id);

        try {
            return Mono.just(mapper.toDto(getById(id, abstractAuthenticationToken)));
        } catch (IOException e) {
            return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound"));
        }
    }

    protected E getById(String id, AbstractAuthenticationToken abstractAuthenticationToken) throws IOException {
        GetResponse<E> getResponse = openSearchService.get(builder -> builder.index(getIndex(entityName, abstractAuthenticationToken)).id(id), classEntity);
        E entity = getResponse.source();

        if (entity != null && Objects.equals(entity.getDeleted(), Boolean.FALSE)) {
            entity.setId(id);
            return entity;
        }

        throw new IOException();
    }

    protected List<Query> getQueries(C criteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        List<Query> queries = new ArrayList<>();
        queries.add(Query.of(f -> f.exists(e -> e.field("deleted"))));
        queries.add(Query.of(f -> f.match(m -> m.field("deleted").query(value -> value.booleanValue(false)))));
        queries.addAll(Converter.stringFilterToQuery("_id", criteria.getId()));
        queries.addAll(Converter.stringFilterToQuery("name.keyword", criteria.getName()));
        queries.addAll(Converter.stringFilterToQuery("description", criteria.getDescription()));

        return queries;
    }

    private Page<D> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) throws IOException {
        List<Query> queries = getQueries(criteria, abstractAuthenticationToken);

        SearchResponse<E> searchResponse = openSearchService.search(searchRequest -> searchRequest
            .index(getIndex(entityName, abstractAuthenticationToken))
            .from(pageable.getPageNumber() * pageable.getPageSize())
            .size(pageable.getPageSize())
            .trackTotalHits(t -> t.enabled(true))
            .query(q -> q.bool(b -> b.must(queries)))
            .sort(pageable.getSort().get().map(sort -> SortOptions.of(fn -> fn.field(fs -> fs.field(Converter.camelCaseToSnakeCase(sort.getProperty())).order(sort.isAscending() ? SortOrder.Asc : SortOrder.Desc)))).toList()), classEntity);

        if (searchResponse == null || searchResponse.hits().hits().isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0L);
        }

        List<D> list = searchResponse.hits().hits().stream().map(hit -> {
            D dto = mapper.toDto(hit.source());
            dto.setId(hit.id());
            return dto;
        }).toList();

        return new PageImpl<>(list, pageable, searchResponse.hits().total().value());
    }

    private String getIndex(String indexName, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);

        if (tenantId == null) {
            tenantId = "";
        }

        return Converter.camelCaseToKebabCase(Converter.tenantConcatSnakeCase(tenantId, indexName));
    }
}
