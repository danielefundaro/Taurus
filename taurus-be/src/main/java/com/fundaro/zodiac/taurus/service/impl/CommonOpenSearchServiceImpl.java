package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.StateFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
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
import org.opensearch.client.opensearch.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import tech.jhipster.service.filter.StringFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CommonOpenSearchServiceImpl<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria, M extends EntityOpenSearchMapper<D, E>> implements CommonOpenSearchService<E, D, C> {

    private final OpenSearchService openSearchService;
    private final IndexResolver indexResolver;
    private final Logger log;
    private final M mapper;
    private final Class<E> classEntity;
    private final String entityName;

    public <T extends CommonOpenSearchService<E, D, C>> CommonOpenSearchServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, M mapper, Class<T> logClass, Class<E> classEntity) {
        this.openSearchService = openSearchService;
        this.indexResolver = indexResolver;
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
    public D save(D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to save {} : {}", entityName, dto);

        if (dto.getId() != null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("A new %s cannot have an existing ID", entityName), entityName, "id.exists");
        }

        try {
            return mapper.toDto(saveEntity(null, mapper.toEntity(dto), abstractAuthenticationToken, tenantId));
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error occurred while saving %s info.\n%s", entityName, e.getMessage()), entityName, "generic");
        }
    }

    @Override
    public D update(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to update {} : {}", entityName, dto);
        return updateDto(id, dto, abstractAuthenticationToken, tenantId);
    }

    @Override
    public D partialUpdate(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to partially update {} : {}", entityName, dto);
        return updateDto(id, dto, abstractAuthenticationToken, tenantId);
    }

    @Override
    public Page<D> findEntitiesByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to get all {} by Criteria", entityName);

        try {
            return findByCriteria(criteria, pageable, abstractAuthenticationToken, tenantId);
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error occurred while getting information of %s: %s", entityName, e.getMessage()), entityName, "generic");
        }
    }

    @Override
    public Optional<D> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to get {} : {}", entityName, id);

        try {
            return Optional.of(mapper.toDto(getById(id, tenantId)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public long count(C criteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to count all {} by Criteria", entityName);

        try {
            return countByCriteria(criteria, tenantId);
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public D delete(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to delete {} : {}", entityName, id);

        try {
            E entity = getById(id, tenantId);
            entity.setDeleted(true);
            saveEntity(id, entity, abstractAuthenticationToken, tenantId);
            return mapper.toDto(entity);
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound");
        }
    }

    protected D deletePermanently(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        log.debug("Request to permanently delete {} : {}", entityName, id);

        try {
            E entity = getByIdIncludingDeleted(id, tenantId);
            if (!openSearchService.deleteDocument(indexResolver.resolve(entityName, tenantId), id)) {
                throw new IOException("Document was not deleted");
            }
            return mapper.toDto(entity);
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound");
        }
    }

    protected Optional<D> findOneIncludingDeleted(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        try {
            return Optional.of(mapper.toDto(getByIdIncludingDeleted(id, tenantId)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void alignChildrenInformation(String childId, AbstractAuthenticationToken abstractAuthenticationToken, Function<StringFilter, C> criteriaFunction, BiFunction<D, String, Boolean> function) {
        String tenantId = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        if (tenantId == null) {
            tenantId = "";
        }

        StringFilter stringFilter = new StringFilter();
        stringFilter.setEquals(childId);
        C c = criteriaFunction.apply(stringFilter);

        int pageNumber = 0, size = 20;
        Page<D> result;

        do {
            Pageable pageable = PageRequest.of(pageNumber++, size);
            try {
                result = findByCriteria(c, pageable, abstractAuthenticationToken, tenantId);
            } catch (IOException ignored) {
                break;
            }

            final String resolvedTenant = tenantId;
            result.getContent().forEach(dto -> {
                if (function.apply(dto, childId)) {
                    partialUpdateSync(dto.getId(), dto, abstractAuthenticationToken, resolvedTenant);
                }
            });
        } while (result != null && result.getTotalPages() > pageNumber);
    }

    protected E getById(String id, String tenantId) throws IOException {
        E entity = getByIdIncludingDeleted(id, tenantId);
        if (!Objects.equals(entity.getDeleted(), Boolean.FALSE)) {
            throw new IOException(String.format("%s with id %s not found", entityName, id));
        }
        return entity;
    }

    private E getByIdIncludingDeleted(String id, String tenantId) throws IOException {
        GetResponse<E> getResponse = openSearchService.get(builder -> builder.index(indexResolver.resolve(entityName, tenantId)).id(id), classEntity);
        E entity = getResponse.source();

        if (entity != null) {
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
        queries.addAll(Converter.stringFilterToQuery("name.keyword", criteria.getName()));
        queries.addAll(Converter.stringFilterToQuery("description", criteria.getDescription()));

        return queries;
    }

    private D updateDto(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken, String tenantId) {
        if (dto.getId() == null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid id", entityName, "id.null");
        }

        if (!Objects.equals(id, dto.getId())) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid ID", entityName, "id.invalid");
        }

        E existingEntity;
        try {
            existingEntity = getById(id, tenantId);
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound");
        }

        mapper.partialUpdate(existingEntity, dto);

        try {
            return mapper.toDto(saveEntity(id, existingEntity, abstractAuthenticationToken, tenantId));
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("Error occurred while update %s info.\n%s", entityName, e.getMessage()), entityName, "generic");
        }
    }

    private void partialUpdateSync(String id, D dto, AbstractAuthenticationToken token, String tenantId) {
        if (dto.getId() == null || !Objects.equals(id, dto.getId())) return;
        E existingEntity;
        try {
            existingEntity = getById(id, tenantId);
        } catch (IOException e) {
            return;
        }
        mapper.partialUpdate(existingEntity, dto);
        try {
            saveEntity(id, existingEntity, token, tenantId);
        } catch (IOException ignored) {
        }
    }

    private E saveEntity(String id, E entity, AbstractAuthenticationToken abstractAuthenticationToken, String tenantId) throws IOException {
        addAuditInfo(entity, abstractAuthenticationToken);

        if (entity instanceof StateFieldsOpenSearch stateEntity && stateEntity.getState() == null) {
            stateEntity.setState(StateEnum.DRAFT);
        }

        IndexRequest<E> indexRequest = new IndexRequest.Builder<E>().index(indexResolver.resolve(entityName, tenantId)).document(entity).id(id).build();
        IndexResponse indexResponse = openSearchService.index(indexRequest);

        if (indexResponse.result() == Result.Created) {
            entity.setId(indexResponse.id());
        }

        return entity;
    }

    private Page<D> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken, String tenantId) throws IOException {
        List<Query> queries = getQueries(criteria);

        SearchResponse<E> searchResponse = openSearchService.search(searchRequest -> searchRequest
            .index(indexResolver.resolve(entityName, tenantId))
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

    private Long countByCriteria(C criteria, String tenantId) throws IOException {
        List<Query> queries = getQueries(criteria);

        CountResponse countResponse = openSearchService.count(searchRequest -> searchRequest
            .index(indexResolver.resolve(entityName, tenantId))
            .query(q -> q.bool(b -> b.must(queries)))
        );

        return countResponse.count();
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
