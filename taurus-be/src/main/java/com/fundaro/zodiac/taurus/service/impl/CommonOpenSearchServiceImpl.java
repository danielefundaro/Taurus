package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.StateFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.repository.CatalogRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CommonOpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import com.fundaro.zodiac.taurus.service.mapper.EntityOpenSearchMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.RangeFilter;
import tech.jhipster.service.filter.StringFilter;

/**
 * Transitional generic relational CRUD for the catalog entities formerly stored in OpenSearch.
 * The class name is retained temporarily so existing resources can move without an API break.
 */
@Transactional
public class CommonOpenSearchServiceImpl<
    E extends CommonFieldsOpenSearch,
    D extends CommonFieldsOpenSearchDTO,
    C extends CommonOpenSearchCriteria,
    M extends EntityOpenSearchMapper<D, E>,
    R extends CatalogRepository<E>
> implements CommonOpenSearchService<E, D, C> {

    private final Logger log;
    private final R repository;
    private final M mapper;
    private final String entityName;

    protected CommonOpenSearchServiceImpl(R repository, M mapper, Class<?> logClass, Class<E> entityClass) {
        this.repository = repository;
        this.mapper = mapper;
        this.log = LoggerFactory.getLogger(logClass);
        this.entityName = entityClass.getSimpleName();
    }

    protected R getRepository() { return repository; }
    public M getMapper() { return mapper; }
    public Logger getLogger() { return log; }
    public String getEntityName() { return entityName; }

    protected D saveEntity(E entity, AbstractAuthenticationToken token, boolean created) {
        prepareForSave(entity, token, created);
        return mapper.toDto(repository.save(entity));
    }

    @Override
    public D save(D dto, AbstractAuthenticationToken token) {
        if (dto.getId() != null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "A new entity cannot already have an ID", entityName, "id.exists");
        }
        E entity = mapper.toEntity(dto);
        prepareForSave(entity, token, true);
        return mapper.toDto(repository.save(entity));
    }

    @Override
    public D update(Long id, D dto, AbstractAuthenticationToken token) {
        validateId(id, dto);
        E entity = repository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> notFound());
        mapper.partialUpdate(entity, dto);
        prepareForSave(entity, token, false);
        return mapper.toDto(repository.save(entity));
    }

    @Override
    public D partialUpdate(Long id, D dto, AbstractAuthenticationToken token) {
        return update(id, dto, token);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<D> findEntitiesByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken token) {
        return repository.findAll(buildSpecification(criteria), JpaPageableUtils.normalize(pageable)).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<D> findOne(Long id, AbstractAuthenticationToken token) {
        return repository.findByIdAndDeletedFalse(id).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    protected Optional<D> findOneIncludingDeleted(Long id) {
        return repository.findById(id).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(C criteria, AbstractAuthenticationToken token) {
        return repository.count(buildSpecification(criteria));
    }

    @Override
    public D delete(Long id, AbstractAuthenticationToken token) {
        E entity = repository.findByIdAndDeletedFalse(id).orElseThrow(() -> notFound());
        entity.setDeleted(true);
        prepareForSave(entity, token, false);
        return mapper.toDto(repository.save(entity));
    }

    protected Specification<E> buildSpecification(C criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (criteria != null) {
                addFilter(predicates, cb, root.get("id"), criteria.getId());
                addStringFilter(predicates, cb, root.get("name"), criteria.getName());
                addStringFilter(predicates, cb, root.get("description"), criteria.getDescription());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    protected void addStringFilter(
        List<Predicate> predicates,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Expression<String> path,
        StringFilter filter
    ) {
        if (filter == null) return;
        if (filter.getEquals() != null) predicates.add(cb.equal(path, filter.getEquals()));
        if (filter.getNotEquals() != null) predicates.add(cb.notEqual(path, filter.getNotEquals()));
        if (filter.getContains() != null) predicates.add(cb.like(cb.lower(path), "%" + filter.getContains().toLowerCase() + "%"));
        if (filter.getDoesNotContain() != null) predicates.add(cb.notLike(cb.lower(path), "%" + filter.getDoesNotContain().toLowerCase() + "%"));
        if (filter.getIn() != null && !filter.getIn().isEmpty()) predicates.add(path.in(filter.getIn()));
    }

    protected <X> void addFilter(
        List<Predicate> predicates,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Expression<X> path,
        Filter<X> filter
    ) {
        if (filter == null) return;
        if (filter.getEquals() != null) predicates.add(cb.equal(path, filter.getEquals()));
        if (filter.getNotEquals() != null) predicates.add(cb.notEqual(path, filter.getNotEquals()));
        if (filter.getIn() != null && !filter.getIn().isEmpty()) predicates.add(path.in(filter.getIn()));
    }

    protected <X extends Comparable<? super X>> void addRangeFilter(
        List<Predicate> predicates,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Expression<X> path,
        RangeFilter<X> filter
    ) {
        addFilter(predicates, cb, path, filter);
        if (filter == null) return;
        if (filter.getGreaterThan() != null) predicates.add(cb.greaterThan(path, filter.getGreaterThan()));
        if (filter.getGreaterThanOrEqual() != null) predicates.add(cb.greaterThanOrEqualTo(path, filter.getGreaterThanOrEqual()));
        if (filter.getLessThan() != null) predicates.add(cb.lessThan(path, filter.getLessThan()));
        if (filter.getLessThanOrEqual() != null) predicates.add(cb.lessThanOrEqualTo(path, filter.getLessThanOrEqual()));
    }

    private void validateId(Long id, D dto) {
        if (id == null || dto.getId() == null || !Objects.equals(id, dto.getId())) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid ID", entityName, "id.invalid");
        }
    }

    private void prepareForSave(E entity, AbstractAuthenticationToken token, boolean created) {
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        Date now = new Date();
        entity.setEditBy(actor);
        entity.setEditDate(now);
        if (created) {
            entity.setInsertBy(actor);
            entity.setInsertDate(now);
            entity.setDeleted(false);
        }
        if (entity instanceof StateFieldsOpenSearch stateEntity && stateEntity.getState() == null) {
            stateEntity.setState(StateEnum.DRAFT);
        }
    }

    private RequestAlertException notFound() {
        return new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound");
    }
}
