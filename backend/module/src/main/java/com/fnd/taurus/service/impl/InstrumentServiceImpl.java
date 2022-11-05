package com.fnd.taurus.service.impl;

import com.fnd.taurus.dto.InstrumentDTO;
import com.fnd.taurus.dto.MediaDTO;
import com.fnd.taurus.entity.Instrument;
import com.fnd.taurus.repository.AuditRepository;
import com.fnd.taurus.repository.InstrumentRepository;
import com.fnd.taurus.service.InstrumentService;
import com.fnd.taurus.service.MediaService;
import org.jetbrains.annotations.NotNull;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class InstrumentServiceImpl implements InstrumentService {
    private final InstrumentRepository instrumentRepository;
    private final AuditRepository auditRepository;
    private final MediaService mediaService;

    @Autowired
    public InstrumentServiceImpl(InstrumentRepository instrumentRepository, AuditRepository auditRepository, MediaService mediaService) {
        this.instrumentRepository = instrumentRepository;
        this.auditRepository = auditRepository;
        this.mediaService = mediaService;
    }

    @Override
    public AuditRepository getAuditRepository() {
        return auditRepository;
    }

    @Override
    public InstrumentRepository getRepository() {
        return instrumentRepository;
    }

    @Override
    public Class<Instrument> getEntity() {
        return Instrument.class;
    }

    @Override
    public Class<InstrumentDTO> getDTO() {
        return InstrumentDTO.class;
    }

    @Override
    public InstrumentDTO saveData(@NotNull InstrumentDTO instrumentDTO, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        InstrumentDTO savedInstrument = InstrumentService.super.saveData(instrumentDTO, keycloakAuthenticationToken);

        savedInstrument.setMedia(mediaList(instrumentDTO.getMedia(), keycloakAuthenticationToken, savedInstrument, mediaService::saveData));

        return savedInstrument;
    }

    @Override
    public InstrumentDTO deleteData(Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        InstrumentDTO deletedInstrument = InstrumentService.super.deleteData(id, keycloakAuthenticationToken);

        if (deletedInstrument.getMedia() != null) {
            deletedInstrument.setMedia(deletedInstrument.getMedia().stream().map(collectionDTO -> {
                collectionDTO.setInstrument(deletedInstrument);
                return mediaService.deleteData(collectionDTO.getId(), keycloakAuthenticationToken);
            }).collect(Collectors.toList()));
        }

        return deletedInstrument;
    }

    private List<MediaDTO> mediaList(List<MediaDTO> dList, KeycloakAuthenticationToken keycloakAuthenticationToken, InstrumentDTO instrumentDTO, BiFunction<MediaDTO, KeycloakAuthenticationToken, MediaDTO> func) {
        if (dList != null) {
            return dList.stream().map(d -> {
                d.setInstrument(instrumentDTO);
                return func.apply(d, keycloakAuthenticationToken);
            }).toList();
        }

        return null;
    }
}
