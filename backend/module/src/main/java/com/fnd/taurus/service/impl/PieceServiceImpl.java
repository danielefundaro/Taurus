package com.fnd.taurus.service.impl;

import com.fnd.taurus.dto.CollectionDTO;
import com.fnd.taurus.dto.MediaDTO;
import com.fnd.taurus.dto.PieceDTO;
import com.fnd.taurus.entity.Media;
import com.fnd.taurus.entity.Piece;
import com.fnd.taurus.repository.AuditRepository;
import com.fnd.taurus.repository.MediaRepository;
import com.fnd.taurus.repository.PieceRepository;
import com.fnd.taurus.service.CollectionService;
import com.fnd.taurus.service.MediaService;
import com.fnd.taurus.service.PieceService;
import org.jetbrains.annotations.NotNull;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PieceServiceImpl implements PieceService {
    private final PieceRepository pieceRepository;
    private final AuditRepository auditRepository;
    private final MediaRepository mediaRepository;
    private final CollectionService collectionService;
    private final MediaService mediaService;

    @Autowired
    public PieceServiceImpl(PieceRepository pieceRepository, AuditRepository auditRepository, MediaRepository mediaRepository, CollectionService collectionService, MediaService mediaService) {
        this.pieceRepository = pieceRepository;
        this.auditRepository = auditRepository;
        this.mediaRepository = mediaRepository;
        this.collectionService = collectionService;
        this.mediaService = mediaService;
    }

    @Override
    public AuditRepository getAuditRepository() {
        return auditRepository;
    }

    @Override
    public PieceRepository getRepository() {
        return pieceRepository;
    }

    @Override
    public Class<Piece> getEntity() {
        return Piece.class;
    }

    @Override
    public Class<PieceDTO> getDTO() {
        return PieceDTO.class;
    }

    @Override
    public PieceDTO saveData(@NotNull PieceDTO pieceDTO, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        PieceDTO savedPiece = PieceService.super.saveData(pieceDTO, keycloakAuthenticationToken);

        // Delete all the media not listed
        savedPiece.getMedia().forEach(mediaDTO -> {
            if (pieceDTO.getMedia().stream().filter(mediaDTO1 -> Objects.equals(mediaDTO1.getId(), mediaDTO.getId())).findAny().isEmpty()) {
                mediaService.deleteData(mediaDTO.getId(), keycloakAuthenticationToken);
            }
        });

        savedPiece.setCollections(saveCollectionList(pieceDTO.getCollections(), keycloakAuthenticationToken, savedPiece));
        savedPiece.setMedia(saveMediaList(pieceDTO.getMedia(), keycloakAuthenticationToken, savedPiece));

        return savedPiece;
    }

    @Override
    public PieceDTO deleteData(Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        PieceDTO deletedPiece = PieceService.super.deleteData(id, keycloakAuthenticationToken);

        if (deletedPiece.getCollections() != null) {
            deletedPiece.setCollections(deletedPiece.getCollections().stream().map(collectionDTO -> {
                collectionDTO.setPiece(deletedPiece);
                return collectionService.deleteData(collectionDTO.getId(), keycloakAuthenticationToken);
            }).toList());
        }

        if (deletedPiece.getMedia() != null) {
            deletedPiece.setMedia(deletedPiece.getMedia().stream().map(mediaDTO -> {
                mediaDTO.setPiece(deletedPiece);
                return mediaService.deleteData(mediaDTO.getId(), keycloakAuthenticationToken);
            }).toList());
        }

        return deletedPiece;
    }

    private List<CollectionDTO> saveCollectionList(List<CollectionDTO> dList, KeycloakAuthenticationToken keycloakAuthenticationToken, PieceDTO pieceDTO) {
        if (dList != null) {
            return dList.stream().map(d -> {
                d.setPiece(pieceDTO);
                return collectionService.saveData(d, keycloakAuthenticationToken);
            }).toList();
        }

        return null;
    }

    private List<MediaDTO> saveMediaList(List<MediaDTO> dList, KeycloakAuthenticationToken keycloakAuthenticationToken, PieceDTO pieceDTO) {
        if (dList != null) {
            return dList.stream().map(d -> {
                d.setPiece(pieceDTO);

                if (d.getId() != null) {
                    Optional<Media> optionalMedia = mediaRepository.findById(d.getId());

                    if (optionalMedia.isPresent()) {
                        MediaDTO mediaDTO = modelMapper.map(optionalMedia.get(), MediaDTO.class);
                        d.setPath(mediaDTO.getPath());
                    }
                }

                return mediaService.saveData(d, keycloakAuthenticationToken);
            }).toList();
        }

        return null;
    }
}
