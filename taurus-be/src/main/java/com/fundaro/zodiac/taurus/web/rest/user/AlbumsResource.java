package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.user.AlbumsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller of ROLE_USER for getting {@link Albums}.
 */
@RestController("LowPermissionsAlbumResource")
@RequestMapping("/api/user/albums")
public class AlbumsResource extends CommonOpenSearchResource<Albums, AlbumsDTO, AlbumsCriteria, AlbumsService> {

    public AlbumsResource(AlbumsService service) {
        super(service, Albums.class.getSimpleName(), AlbumsResource.class);
    }
}
