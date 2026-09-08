package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
     * @return the {@link ResponseEntity} with status {@code 202 (Accepted)} and the queued upload job.
     */
    @PostMapping(value = "/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QueueUploadFilesDTO> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "annotations", required = false) String annotations,
            AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to upload media {}", getEntityName());
        QueueUploadFilesDTO job = getService().uploadFile(null, file, annotations, abstractAuthenticationToken);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /**
     * {@code POST  /:id/stream} : Save a new file.
     *
     * @param id the "id" of entity.
     * @return the {@link ResponseEntity} with status {@code 202 (Accepted)} and the queued upload job.
     */
    @PostMapping(value = "/{id}/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QueueUploadFilesDTO> uploadMedia(
            @PathVariable(value = "id") final Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "annotations", required = false) String annotations,
            AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().debug("REST request to upload {} : {}", getEntityName(), id);
        QueueUploadFilesDTO job = getService().uploadFile(id, file, annotations, abstractAuthenticationToken);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/{id}/upload-jobs")
    public ResponseEntity<List<QueueUploadFilesDTO>> getUploadJobs(
        @PathVariable("id") Long id,
        AbstractAuthenticationToken abstractAuthenticationToken
    ) {
        return ResponseEntity.ok(getService().findUploadJobs(id, abstractAuthenticationToken));
    }

    @PostMapping("/{id}/upload-jobs/{jobId}/retry")
    public ResponseEntity<QueueUploadFilesDTO> retryUploadJob(
        @PathVariable("id") Long id,
        @PathVariable("jobId") Long jobId,
        AbstractAuthenticationToken abstractAuthenticationToken
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(getService().retryUploadJob(id, jobId, abstractAuthenticationToken));
    }
}
