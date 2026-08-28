package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link Tracks}.
 */
@RestController
@RequestMapping("/api/tracks")
public class TracksResource extends CommonOpenSearchResource<Tracks, TracksDTO, TracksCriteria, TracksService> {

    public TracksResource(TracksService service) {
        super(service, Tracks.class.getSimpleName(), TracksResource.class);
    }

    /**
     * {@code POST  /stream} : Save a new file.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the stream of the file, or with status {@code 400 (Bad Request)} if the media has not exists.
     */
    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "annotations", required = false) String annotations,
            AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to upload media {}", getEntityName());
        getService().uploadFile(null, file, annotations, abstractAuthenticationToken);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(getApplicationName(), false, getEntityName(), ""))
            .build();
    }

    /**
     * {@code POST  /:id/stream} : Save a new file.
     *
     * @param id the "id" of entity.
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the stream of the file, or with status {@code 400 (Bad Request)} if the media has not exists.
     */
    @PostMapping(value = "/{id}/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadMedia(
            @PathVariable(value = "id") final Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "annotations", required = false) String annotations,
            AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to upload {} : {}", getEntityName(), id);
        getService().uploadFile(id, file, annotations, abstractAuthenticationToken);
        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(getApplicationName(), false, getEntityName(), id.toString()))
            .build();
    }
}
