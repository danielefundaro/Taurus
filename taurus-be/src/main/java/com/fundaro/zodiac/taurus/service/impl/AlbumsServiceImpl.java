package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.repository.AlbumsRepository;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Albums}.
 */
@Service
@Transactional
public class AlbumsServiceImpl extends CommonServiceImpl<Albums, AlbumsDTO, AlbumsCriteria, AlbumsMapper, AlbumsRepository> implements AlbumsService {

    public AlbumsServiceImpl(AlbumsRepository albumsRepository, AlbumsMapper albumsMapper) {
        super(albumsRepository, albumsMapper, AlbumsService.class, Albums.class.getSimpleName());
    }
}
