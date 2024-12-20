package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.domain.criteria.PiecesCriteria;
import com.fundaro.zodiac.taurus.service.PiecesService;
import com.fundaro.zodiac.taurus.service.dto.PiecesDTO;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.reactive.ResponseUtil;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Pieces}.
 */
@RestController
@RequestMapping("/api/pieces")
public class PiecesResource extends CommonResource<Pieces, PiecesDTO, PiecesCriteria, PiecesService> {

    public PiecesResource(PiecesService piecesService) {
        super(piecesService, PiecesResource.class, Pieces.class.getSimpleName());
    }
}
