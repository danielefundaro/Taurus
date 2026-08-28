package com.fundaro.zodiac.taurus.web.rest.external;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.user.AlbumsService;
import com.fundaro.zodiac.taurus.web.rest.user.CommonOpenSearchResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller of ROLE_USER_EXTERNAL for getting {@link Albums} (PUBLIC state only).
 */
@RestController("ExternalPermissionsAlbumResource")
@RequestMapping("/api/external/albums")
public class AlbumsResource extends CommonOpenSearchResource<Albums, AlbumsDTO, AlbumsCriteria, AlbumsService> {

    public AlbumsResource(@Qualifier("ExternalPermissionsAlbumsService") AlbumsService service) {
        super(service, Albums.class.getSimpleName(), AlbumsResource.class);
    }
}
