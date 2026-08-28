package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import com.fundaro.zodiac.taurus.service.user.CommonOpenSearchService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

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
}
