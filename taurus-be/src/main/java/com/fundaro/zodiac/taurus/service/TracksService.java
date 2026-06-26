package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Interface for managing {@link Tracks}.
 */
public interface TracksService extends CommonOpenSearchService<Tracks, TracksDTO, TracksCriteria> {
    void uploadFile(String id, MultipartFile file, AbstractAuthenticationToken abstractAuthenticationToken);
}
