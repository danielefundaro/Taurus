package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.CommonOpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * REST controller for managing {@link CommonFieldsOpenSearch}.
 */
public class CommonOpenSearchResource<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria, S extends CommonOpenSearchService<E, D, C>> {

    private final Logger log;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final S service;

    private final String entityName;

    public <T extends CommonOpenSearchResource<E, D, C, S>> CommonOpenSearchResource(S service, String entityName, Class<T> classResource) {
        this.service = service;
        this.entityName = entityName;
        this.log = LoggerFactory.getLogger(classResource);
    }

    public Logger getLog() {
        return log;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public S getService() {
        return service;
    }

    public String getEntityName() {
        return entityName;
    }

    /**
     * {@code POST  /} : Create a new entity.
     *
     * @param dto the dto to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new dto, or with status {@code 400 (Bad Request)} if the entity has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping({"", "/"})
    public ResponseEntity<D> createEntity(@Valid @RequestBody D dto, AbstractAuthenticationToken abstractAuthenticationToken) throws URISyntaxException {
        log.debug("REST request to save {} : {}", entityName, dto);
        D result = service.save(dto, abstractAuthenticationToken);
        return ResponseEntity.created(new URI(String.format("/api/%s/", entityName) + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, entityName, result.getId().toString()))
            .body(result);
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
    public ResponseEntity<D> updateEntity(@PathVariable(value = "id", required = false) final Long id, @Valid @RequestBody D dto, AbstractAuthenticationToken abstractAuthenticationToken) throws URISyntaxException {
        log.debug("REST request to update {} : {}, {}", entityName, id, dto);
        D result = service.update(id, dto, abstractAuthenticationToken);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, entityName, result.getId().toString()))
            .body(result);
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
    public ResponseEntity<D> partialUpdateEntity(@PathVariable(value = "id", required = false) final Long id, @Valid @NotNull @RequestBody D dto, AbstractAuthenticationToken abstractAuthenticationToken) throws URISyntaxException {
        log.debug("REST request to partial update {} partially : {}, {}", entityName, id, dto);
        D result = service.partialUpdate(id, dto, abstractAuthenticationToken);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, entityName, result.getId().toString()))
            .body(result);
    }

    /**
     * {@code GET  /} : get all the entity.
     *
     * @param pageable the pagination information.
     * @param request  a {@link HttpServletRequest} request.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of entity in body.
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<D>> getAllEntities(C criteria, @ParameterObject Pageable pageable, HttpServletRequest request, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to get {} by criteria: {}", entityName, criteria);
        Page<D> page = service.findEntitiesByCriteria(criteria, pageable, abstractAuthenticationToken);
        return ResponseEntity.ok()
            .headers(PaginationUtil.generatePaginationHttpHeaders(UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString()), page))
            .body(page);
    }

    /**
     * {@code GET  /:id} : get the "id" entity.
     *
     * @param id the id of the dto to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dto, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<D> getEntity(@PathVariable("id") Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to get {} : {}", entityName, id);
        return service.findOne(id, abstractAuthenticationToken)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * {@code DELETE  /:id} : delete the "id" entity.
     *
     * @param id the id of the dto to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable("id") Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to delete {} : {}", entityName, id);
        service.delete(id, abstractAuthenticationToken);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, entityName, id.toString()))
            .build();
    }
}
