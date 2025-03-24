package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for managing {@link Media}.
 */
@RestController
@RequestMapping("/api/media")
public class MediaResource extends CommonOpenSearchResource<Media, MediaDTO, MediaCriteria, MediaService> {

    public MediaResource(MediaService service) {
        super(service, "Media", MediaResource.class);
    }

    /**
     * {@code GET  /:id/stream} : Stream the file.
     *
     * @param id the "id" of entity.
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the stream of the file, or with status {@code 400 (Bad Request)} if the media has not exists.
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<DataBuffer>>> streamMedia(@PathVariable(value = "id") final String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to stream {} : {}", getEntityName(), id);
        return getService().streamFile(id, abstractAuthenticationToken).map(result ->
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment: image.jpg")
                .body(result)
        );
    }
}
