package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.domain.criteria.PiecesCriteria;
import com.fundaro.zodiac.taurus.repository.PiecesRepository;
import com.fundaro.zodiac.taurus.service.PiecesService;
import com.fundaro.zodiac.taurus.service.dto.PiecesDTO;
import com.fundaro.zodiac.taurus.service.mapper.PiecesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Pieces}.
 */
@Service
@Transactional
public class PiecesServiceImpl extends CommonServiceImpl<Pieces, PiecesDTO, PiecesCriteria, PiecesMapper, PiecesRepository> implements PiecesService {

    public PiecesServiceImpl(PiecesRepository piecesRepository, PiecesMapper piecesMapper) {
        super(piecesRepository, piecesMapper, PiecesService.class, Pieces.class.getSimpleName());
    }
}
