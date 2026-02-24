package com.fundaro.zodiac.taurus.service.user;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;

public interface AlbumsService extends CommonOpenSearchService<Albums, AlbumsDTO, AlbumsCriteria> {
}
