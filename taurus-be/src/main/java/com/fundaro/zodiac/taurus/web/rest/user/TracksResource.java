package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.user.TracksService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller of ROLE_USER for getting {@link Tracks}.
 */
@RestController("LowPermissionsTracksResource")
@RequestMapping("/api/user/tracks")
public class TracksResource extends CommonOpenSearchResource<Tracks, TracksDTO, TracksCriteria, TracksService> {

    public TracksResource(TracksService service) {
        super(service, Tracks.class.getSimpleName(), TracksResource.class);
    }
}
