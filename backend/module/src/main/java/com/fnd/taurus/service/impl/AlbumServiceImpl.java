package com.fnd.taurus.service.impl;

import com.fnd.taurus.dto.AlbumDTO;
import com.fnd.taurus.dto.CollectionDTO;
import com.fnd.taurus.entity.Album;
import com.fnd.taurus.repository.AlbumRepository;
import com.fnd.taurus.repository.AuditRepository;
import com.fnd.taurus.service.AlbumService;
import com.fnd.taurus.service.CollectionService;
import org.jetbrains.annotations.NotNull;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class AlbumServiceImpl implements AlbumService {
    private final AlbumRepository albumRepository;
    private final AuditRepository auditRepository;
    private final CollectionService collectionService;

    @Autowired
    public AlbumServiceImpl(AlbumRepository albumRepository, AuditRepository auditRepository, CollectionService collectionService) {
        this.albumRepository = albumRepository;
        this.auditRepository = auditRepository;
        this.collectionService = collectionService;
    }

    @Override
    public AuditRepository getAuditRepository() {
        return auditRepository;
    }

    @Override
    public AlbumRepository getRepository() {
        return albumRepository;
    }

    @Override
    public Class<Album> getEntity() {
        return Album.class;
    }

    @Override
    public Class<AlbumDTO> getDTO() {
        return AlbumDTO.class;
    }

    @Override
    public AlbumDTO saveData(@NotNull AlbumDTO albumDTO, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        AlbumDTO savedAlbum = AlbumService.super.saveData(albumDTO, keycloakAuthenticationToken);

        // Delete all the media not listed
        savedAlbum.getCollections().forEach(collectionDTO -> {
            if (albumDTO.getCollections().stream().filter(collectionDTO1 -> Objects.equals(collectionDTO1.getId(), collectionDTO.getId())).findAny().isEmpty()) {
                collectionService.deleteData(collectionDTO.getId(), keycloakAuthenticationToken);
            }
        });

        savedAlbum.setCollections(collectionList(albumDTO.getCollections(), keycloakAuthenticationToken, savedAlbum, collectionService::saveData));

        return savedAlbum;
    }

    @Override
    public AlbumDTO deleteData(Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        AlbumDTO deletedAlbum = AlbumService.super.deleteData(id, keycloakAuthenticationToken);

        if (deletedAlbum.getCollections() != null) {
            deletedAlbum.setCollections(deletedAlbum.getCollections().stream().map(collectionDTO -> {
                collectionDTO.setAlbum(deletedAlbum);
                return collectionService.deleteData(collectionDTO.getId(), keycloakAuthenticationToken);
            }).collect(Collectors.toList()));
        }

        return deletedAlbum;
    }

    private List<CollectionDTO> collectionList(List<CollectionDTO> dList, KeycloakAuthenticationToken keycloakAuthenticationToken, AlbumDTO albumDTO, BiFunction<CollectionDTO, KeycloakAuthenticationToken, CollectionDTO> func) {
        if (dList != null) {
            return dList.stream().map(d -> {
                d.setAlbum(albumDTO);
                return func.apply(d, keycloakAuthenticationToken);
            }).toList();
        }

        return null;
    }
}
