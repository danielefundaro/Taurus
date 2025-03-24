package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link Albums}.
 */
@Service
@Transactional
public class AlbumsServiceImpl extends CommonOpenSearchServiceImpl<Albums, AlbumsDTO, AlbumsCriteria, AlbumsMapper> implements AlbumsService {

    public AlbumsServiceImpl(OpenSearchService openSearchService, AlbumsMapper albumsMapper) {
        super(openSearchService, albumsMapper, AlbumsService.class, Albums.class, "Albums");
    }
}
