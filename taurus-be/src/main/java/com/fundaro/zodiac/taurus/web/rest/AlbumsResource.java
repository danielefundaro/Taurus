package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Albums}.
 */
@RestController
@RequestMapping("/api/albums")
public class AlbumsResource extends CommonResource<Albums, AlbumsDTO, AlbumsCriteria, AlbumsService> {

    public AlbumsResource(AlbumsService service) {
        super(service, AlbumsResource.class, Albums.class.getSimpleName());
    }
}
