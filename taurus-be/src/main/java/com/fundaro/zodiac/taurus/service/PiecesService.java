package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.domain.criteria.PiecesCriteria;
import com.fundaro.zodiac.taurus.service.dto.PiecesDTO;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Pieces}.
 */
public interface PiecesService extends CommonService<Pieces, PiecesDTO, PiecesCriteria> {
}
