package com.fundaro.zodiac.taurus.service.user;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MediaService extends CommonOpenSearchService<Media, MediaDTO, MediaCriteria> {
    Mono<Flux<DataBuffer>> streamFile(String id, AbstractAuthenticationToken abstractAuthenticationToken);
}
