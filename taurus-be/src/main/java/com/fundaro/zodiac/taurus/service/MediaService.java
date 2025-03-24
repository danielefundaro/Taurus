package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link Media}.
 */
public interface MediaService extends CommonOpenSearchService<Media, MediaDTO, MediaCriteria> {
    Mono<Flux<DataBuffer>> streamFile(String id, AbstractAuthenticationToken abstractAuthenticationToken);
}
