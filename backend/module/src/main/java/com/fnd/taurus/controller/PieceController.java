package com.fnd.taurus.controller;

import com.fnd.taurus.dto.PieceDTO;
import com.fnd.taurus.entity.Piece;
import com.fnd.taurus.repository.PieceRepository;
import com.fnd.taurus.service.PieceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(value = "pieces")
@RestController
@Validated
public class PieceController implements CommonController<Piece, PieceDTO, PieceRepository, PieceService> {
    private final PieceService pieceService;

    @Autowired
    public PieceController(PieceService pieceService) {
        this.pieceService = pieceService;
    }

    @Override
    public PieceService getService() {
        return pieceService;
    }
}
