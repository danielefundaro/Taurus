package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Media}.
 */
public interface MediaService extends CommonService<Media, MediaDTO, MediaCriteria> {
    Flux<Void> uploadFile(Flux<FilePart> filePartFlux, AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Resource> streamFile(Long id, AbstractAuthenticationToken abstractAuthenticationToken);
}
