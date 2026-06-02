package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
@RestController
@RequestMapping("/api/notices")
public class NoticesResource extends CommonResource<Notices, NoticesDTO, NoticesCriteria, NoticesService> {

    public NoticesResource(NoticesService noticesService) {
        super(noticesService, NoticesResource.class, Notices.class.getSimpleName());
    }

    @PatchMapping("/read-all")
    public Mono<ResponseEntity<Void>> readAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to mark all notices as read");

        return getService().readAll(abstractAuthenticationToken)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PatchMapping("/{id}/read")
    public Mono<ResponseEntity<NoticesDTO>> read(@PathVariable Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to set notice {} as already read", id);

        return getService().read(id, abstractAuthenticationToken).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))).map(res ->
            ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(getApplicationName(), false, getEntityName(), res.getId().toString()))
                .body(res)
        );
    }

    @DeleteMapping("delete-all")
    public Mono<ResponseEntity<Void>> deleteAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to delete all notices");

        return getService().deleteAll(abstractAuthenticationToken)
            .then(Mono.just(ResponseEntity.noContent().build())
        );
    }
}
