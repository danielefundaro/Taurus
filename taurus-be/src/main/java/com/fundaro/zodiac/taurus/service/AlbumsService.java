package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Albums}.
 */
public interface AlbumsService extends CommonService<Albums, AlbumsDTO, AlbumsCriteria> {
}
