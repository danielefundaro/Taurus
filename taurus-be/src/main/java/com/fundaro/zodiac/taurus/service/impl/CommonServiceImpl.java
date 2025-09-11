package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import com.fundaro.zodiac.taurus.domain.criteria.CommonCriteria;
import com.fundaro.zodiac.taurus.repository.CommonRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CommonService;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsDTO;
import com.fundaro.zodiac.taurus.service.mapper.EntityMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.Objects;

@Transactional
public class CommonServiceImpl<E extends CommonFields, D extends CommonFieldsDTO, C extends CommonCriteria, M extends EntityMapper<D, E>, R extends CommonRepository<E, C>> implements CommonService<E, D, C> {

    private final Logger log;

    private final R repository;

    private final M mapper;

    private final String entityName;

    public <T extends CommonService<E, D, C>> CommonServiceImpl(R repository, M mapper, Class<T> logClass, String entityName) {
        this.repository = repository;
        this.mapper = mapper;
        this.log = LoggerFactory.getLogger(logClass);
        this.entityName = entityName;
    }

    public Logger getLog() {
        return log;
    }

    public String getEntityName() {
        return entityName;
    }

    @Override
    public Mono<D> save(D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to save {} : {}", entityName, dto);

        if (dto.getId() != null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, String.format("A new %s cannot already have an ID", entityName), entityName, "id.exists");
        }

        setUserIdDto(dto, abstractAuthenticationToken);
        return saveEntity(mapper.toEntity(dto), abstractAuthenticationToken).map(mapper::toDto);
    }

    @Override
    public Mono<D> update(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to update {} : {}", entityName, dto);

        if (dto.getId() == null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid id", entityName, "id.null");
        }

        if (!Objects.equals(id, dto.getId())) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid ID", entityName, "id.invalid");
        }

        String userId = setUserIdDto(dto, abstractAuthenticationToken);
        return repository.findByIdAndUserId(id, userId).flatMap(existingEntity -> {
            if (existingEntity == null) {
                return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound"));
            }

            mapper.partialUpdate(existingEntity, dto);
            return saveEntity(existingEntity, abstractAuthenticationToken);
        }).map(mapper::toDto);
    }

    @Override
    public Mono<D> partialUpdate(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to partially update {} : {}", entityName, dto);

        if (dto.getId() == null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid id", entityName, "id.null");
        }
        if (!Objects.equals(id, dto.getId())) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid ID", entityName, "id.invalid");
        }

        String userId = setUserIdDto(dto, abstractAuthenticationToken);
        return repository.findByIdAndUserId(dto.getId(), userId).flatMap(existingEntity -> {
            if (existingEntity == null) {
                return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", entityName, "id.notFound"));
            }

            mapper.partialUpdate(existingEntity, dto);
            return saveEntity(existingEntity, abstractAuthenticationToken);
        }).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<D> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to get all {} by Criteria", entityName);
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        return repository.findByCriteria(criteria, pageable, userId).map(mapper::toDto);
    }

    /**
     * Find the count of albums by criteria.
     *
     * @param criteria filtering criteria
     * @return the count of albums
     */
    @Override
    public Mono<Long> countByCriteria(C criteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to get the count of all {} by Criteria", entityName);
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        return repository.countByCriteria(criteria, userId);
    }

    @Override
    public Mono<Long> countAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        return repository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<D> findOne(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to get {} : {}", entityName, id);
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        return repository.findByIdAndUserId(id, userId).map(mapper::toDto);
    }

    @Override
    public Mono<Void> delete(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("Request to delete {} : {}", entityName, id);
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        return repository.deleteByIdAndUserId(id, userId);
    }

    private String setUserIdDto(D dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        dto.setUserId(userId);
        return userId;
    }

    private Mono<E> saveEntity(E entity, AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        entity.setEditBy(userId);
        entity.setEditDate(ZonedDateTime.now());
        entity.setUserId(userId);

        if (Strings.isBlank(entity.getInsertBy())) {
            entity.setInsertBy(userId);
            entity.setInsertDate(ZonedDateTime.now());
        }

        if (entity.getDeleted() == null) {
            entity.setDeleted(false);
        }

        return repository.save(entity);
    }
}
