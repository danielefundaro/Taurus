package com.fundaro.zodiac.taurus.web.rest.external;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.user.TracksService;
import com.fundaro.zodiac.taurus.web.rest.user.CommonOpenSearchResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller of ROLE_USER_EXTERNAL for getting {@link Tracks} (PUBLIC state only, no instrument filter).
 */
@RestController("ExternalPermissionsTracksResource")
@RequestMapping("/api/external/tracks")
@PreAuthorize("hasAuthority('ROLE_USER_EXTERNAL')")
public class TracksResource extends CommonOpenSearchResource<Tracks, TracksDTO, TracksCriteria, TracksService> {

    public TracksResource(@Qualifier("ExternalPermissionsTracksService") TracksService service) {
        super(service, Tracks.class.getSimpleName(), TracksResource.class);
    }
}
