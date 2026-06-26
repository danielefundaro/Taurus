package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Service Interface for managing {@link Media}.
 */
public interface MediaService extends CommonOpenSearchService<Media, MediaDTO, MediaCriteria> {
    Resource streamFile(String id, AbstractAuthenticationToken abstractAuthenticationToken);
}
