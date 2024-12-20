package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.reactive.ResponseUtil;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Media}.
 */
@RestController
@RequestMapping("/api/media")
public class MediaResource extends CommonResource<Media, MediaDTO, MediaCriteria, MediaService> {

    public MediaResource(MediaService mediaService) {
        super(mediaService, MediaResource.class, Media.class.getSimpleName());
    }

    /**
     * {@code POST  /:id/stream} : Create a new file.
     *
     * @param id the "id" of entity.
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the stream of the file, or with status {@code 400 (Bad Request)} if the media has not exists.
     */
    @PostMapping(value = "/{id}/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Void>> uploadMedia(@PathVariable(value = "id") final Long id, @RequestPart("file") Flux<FilePart> filePartFlux, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to upload {} : {}", getEntityName(), id);
        return getService().uploadFile(filePartFlux, abstractAuthenticationToken).switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND))).then(Mono.just(
            ResponseEntity.noContent()
                .headers(HeaderUtil.createEntityDeletionAlert(getApplicationName(), false, getEntityName(), id.toString()))
                .build()
        ));
    }

    /**
     * {@code GET  /:id/stream} : Stream the file.
     *
     * @param id the "id" of entity.
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the stream of the file, or with status {@code 400 (Bad Request)} if the media has not exists.
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Resource>> streamMedia(@PathVariable(value = "id") final Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to stream {} : {}", getEntityName(), id);
        return ResponseUtil.wrapOrNotFound(getService().streamFile(id, abstractAuthenticationToken));
    }
}
