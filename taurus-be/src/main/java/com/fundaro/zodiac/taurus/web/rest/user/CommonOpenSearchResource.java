package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import com.fundaro.zodiac.taurus.service.user.CommonOpenSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.ForwardedHeaderUtils;
import reactor.core.publisher.Mono;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.reactive.ResponseUtil;

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
     * @param request  a {@link ServerHttpRequest} request.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of entity in body.
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Page<D>>> getAllEntities(C criteria, @ParameterObject Pageable pageable, ServerHttpRequest request, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to get {} by criteria: {}", entityName, criteria);

        return service.findEntitiesByCriteria(criteria, pageable, abstractAuthenticationToken).map(countWithEntities ->
            ResponseEntity.ok()
                .headers(
                    PaginationUtil.generatePaginationHttpHeaders(
                        ForwardedHeaderUtils.adaptFromForwardedHeaders(request.getURI(), request.getHeaders()),
                        new PageImpl<>(countWithEntities.getContent(), pageable, countWithEntities.getTotalElements())
                    )
                )
                .body(new PageImpl<>(countWithEntities.getContent(), pageable, countWithEntities.getTotalElements()))
        );
    }

    /**
     * {@code GET  /:id} : get the "id" entity.
     *
     * @param id the id of the dto to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the dto, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<D>> getEntity(@PathVariable("id") String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        log.debug("REST request to get {} : {}", entityName, id);
        Mono<D> dto = service.findOne(id, abstractAuthenticationToken);
        return ResponseUtil.wrapOrNotFound(dto);
    }
}
