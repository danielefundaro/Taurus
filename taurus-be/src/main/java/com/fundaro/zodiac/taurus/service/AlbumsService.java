package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;

/**
 * Service Interface for managing {@link Albums}.
 */
public interface AlbumsService extends CommonOpenSearchService<Albums, AlbumsDTO, AlbumsCriteria> {
}
