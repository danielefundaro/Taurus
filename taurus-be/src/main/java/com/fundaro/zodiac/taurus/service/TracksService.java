package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link Tracks}.
 */
public interface TracksService extends CommonOpenSearchService<Tracks, TracksDTO, TracksCriteria> {
    Mono<Void> uploadFile(String id, FilePart filePart, AbstractAuthenticationToken abstractAuthenticationToken);
}
