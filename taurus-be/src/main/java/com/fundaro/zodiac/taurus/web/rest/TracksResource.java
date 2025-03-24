package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tech.jhipster.web.util.HeaderUtil;

import javax.annotation.Nonnull;

/**
 * REST controller for managing {@link Tracks}.
 */
@RestController
@RequestMapping("/api/tracks")
public class TracksResource extends CommonOpenSearchResource<Tracks, TracksDTO, TracksCriteria, TracksService> {

    public TracksResource(TracksService service) {
        super(service, "Tracks", TracksResource.class);
    }

    /**
     * {@code POST  /:id/stream} : Save a new file.
     *
     * @param id the "id" of entity.
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the stream of the file, or with status {@code 400 (Bad Request)} if the media has not exists.
     */
    @PostMapping(value = "/{id}/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Void>> uploadMedia(@PathVariable(value = "id") final String id, @Nonnull @RequestPart("file") FilePart filePart, AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to upload {} : {}", getEntityName(), id);
        return getService().uploadFile(id, filePart, abstractAuthenticationToken)
            .then(Mono.just(
                ResponseEntity.noContent()
                    .headers(HeaderUtil.createEntityDeletionAlert(getApplicationName(), false, getEntityName(), id))
                    .build()
            ));
    }
}
