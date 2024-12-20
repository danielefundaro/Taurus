package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import com.fundaro.zodiac.taurus.domain.criteria.CommonCriteria;
import com.fundaro.zodiac.taurus.service.CommonService;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.ForwardedHeaderUtils;
import reactor.core.publisher.Mono;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.reactive.ResponseUtil;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * REST controller for managing {@link CommonFields}.
 */
public class CommonResource<E extends CommonFields, D extends CommonFieldsDTO, C extends CommonCriteria, S extends CommonService<E, D, C>> {

    private final Logger log;

    private final String entityName;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final S service;

    public <T extends CommonResource<E, D, C, S>> CommonResource(S service, Class<T> classResource, String entityName) {
        this.service = service;
        this.log = LoggerFactory.getLogger(classResource);
        this.entityName = entityName;
    }

    public Logger getLog() {
        return log;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public S getService() {
        return service;
    }

    /**
     * {@code POST  /} : Create a new entity.
     *
     * @param dto the dto to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dto, or with status {@code 400 (Bad Request)} if the entity has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public Mono<ResponseEntity<D>> createEntity(@Valid @RequestBody D dto, AbstractAuthenticationToken abstractAuthenticationToken) throws URISyntaxException {
        log.debug("REST request to save {} : {}", entityName, dto);

        return service.save(dto, abstractAuthenticationToken).handle((result, sink) -> {
            try {
                sink.next(ResponseEntity.created(new URI(String.format("/api/%s/", entityName) + result.getId()))
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, entityName, result.getId().toString()))
                    .body(result));
            } catch (URISyntaxException e) {
                sink.error(new RuntimeException(e));
            }
        });
    }

    /**
     * {@code PUT  /:id} : Updates an existing entity.
     *
     * @param id  the id of the dto to save.
     * @param dto the dto to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dto,
     * or with status {@code 400 (Bad Request)} if the dto is not valid,
     * or with status {@code 500 (Internal Server Error)} if the dto couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<D>> updateEntity(@PathVariable(value = "id", required = false) final Long id, @Valid @RequestBody D dto, AbstractAuthenticationToken abstractAuthenticationToken) throws URISyntaxException {
        log.debug("REST request to update {} : {}, {}", entityName, id, dto);

        return service.update(id, dto, abstractAuthenticationToken).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))).map(result ->
            ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, entityName, result.getId().toString()))
                .body(result)
        );
    }

    /**
     * {@code PATCH  /:id} : Partial updates given fields of an existing entity, field will ignore if it is null
     *
     * @param id  the id of the dto to save.
     * @param dto the dto to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated dto,
     * or with status {@code 400 (Bad Request)} if the dto is not valid,
     * or with status {@code 404 (Not Found)} if the dto is not found,
     * or with status {@code 500 (Internal Server Error)} if the dto couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PatchMapping(value = "/{id}", consumes = {"application/json", "application/merge-patch+json"})
    public Mono<ResponseEntity<D>> partialUpdateEntity(@PathVariable(value = "id", required = false) final Long id, @NotNull @RequestBody D dto, AbstractAuthenticationToken abstractAuthenticationToken) throws URISyntaxException {
        log.debug("REST request to partial update {} partially : {}, {}", entityName, id, dto);

        return service.partialUpdate(id, dto, abstractAuthenticationToken).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))).map(res ->
            ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, entityName, res.getId().toString()))
                .body(res)
        );
    }

    /**
     * {@code GET  /} : get all the entity.
     *
     * @param pageable the pagination information.
     * @param request  a {@link ServerHttpRequest} request.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of entity in body.
     */
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Page<D>>> getAllEntities(C criteria, @ParameterObject Pageable pageable, ServerHttpRequest request, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to get {} by criteria: {}", entityName, criteria);
        return service.countByCriteria(criteria, abstractAuthenticationToken).zipWith(service.findByCriteria(criteria, pageable, abstractAuthenticationToken).collectList()).map(countWithEntities ->
            ResponseEntity.ok()
                .headers(
                    PaginationUtil.generatePaginationHttpHeaders(
                        ForwardedHeaderUtils.adaptFromForwardedHeaders(request.getURI(), request.getHeaders()),
                        new PageImpl<>(countWithEntities.getT2(), pageable, countWithEntities.getT1())
                    )
                )
                .body(new PageImpl<>(countWithEntities.getT2(), pageable, countWithEntities.getT1()))
        );
    }

    /**
     * {@code GET  /count} : count all the entities.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @GetMapping("/count")
    public Mono<ResponseEntity<Long>> countEntities(C criteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to count {} by criteria: {}", entityName, criteria);
        return service.countByCriteria(criteria, abstractAuthenticationToken).map(count -> ResponseEntity.status(HttpStatus.OK).body(count));
    }

    /**
     * {@code GET  /:id} : get the "id" entity.
     *
     * @param id the id of the dto to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dto, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<D>> getEntity(@PathVariable("id") Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to get {} : {}", entityName, id);
        Mono<D> dto = service.findOne(id, abstractAuthenticationToken);
        return ResponseUtil.wrapOrNotFound(dto);
    }

    /**
     * {@code DELETE  /:id} : delete the "id" entity.
     *
     * @param id the id of the dto to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteEntity(@PathVariable("id") Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to delete {} : {}", entityName, id);
        return service.delete(id, abstractAuthenticationToken).then(
            Mono.just(
                ResponseEntity.noContent()
                    .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, entityName, id.toString()))
                    .build()
            )
        );
    }
}
