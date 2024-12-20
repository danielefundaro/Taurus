package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Tracks}.
 */
@RestController
@RequestMapping("/api/tracks")
public class TracksResource extends CommonResource<Tracks, TracksDTO, TracksCriteria, TracksService> {

    public TracksResource(TracksService tracksService) {
        super(tracksService, TracksResource.class, Tracks.class.getSimpleName());
    }
}
