package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.mapper.InstrumentsMapper;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Service Implementation for managing {@link Instruments}.
 */
@Service
@Transactional
public class InstrumentsServiceImpl extends CommonOpenSearchServiceImpl<Instruments, InstrumentsDTO, InstrumentsCriteria, InstrumentsMapper> implements InstrumentsService {

    private final TracksService tracksService;

    public InstrumentsServiceImpl(OpenSearchService openSearchService, InstrumentsMapper instrumentsMapper, TracksService tracksService) {
        super(openSearchService, instrumentsMapper, InstrumentsService.class, Instruments.class, "Instruments");
        this.tracksService = tracksService;
    }

    @Override
    public Mono<InstrumentsDTO> update(String id, InstrumentsDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.update(id, dto, abstractAuthenticationToken).map(instrumentsDTO -> {
            updateRelatedInstruments(id, dto, instrumentsDTO, abstractAuthenticationToken);
            return instrumentsDTO;
        });
    }

    @Override
    public Mono<InstrumentsDTO> partialUpdate(String id, InstrumentsDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.partialUpdate(id, dto, abstractAuthenticationToken).map(instrumentsDTO -> {
            updateRelatedInstruments(id, dto, instrumentsDTO, abstractAuthenticationToken);
            return instrumentsDTO;
        });
    }

    @Override
    public Mono<Boolean> delete(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.delete(id, abstractAuthenticationToken).map(b -> {
            if (b) {
                // Delete all related information
                tracksService.alignChildrenInformation(id, abstractAuthenticationToken, stringFilter -> new TracksCriteria().setInstrumentId(stringFilter), (tracksDTO, s) -> {
                    boolean result = false;

                    if (tracksDTO.getScores() != null) {
                        for (SheetsMusicDTO sheetsMusicDTO : tracksDTO.getScores()) {
                            if (sheetsMusicDTO.getInstruments() != null) {
                                result |= sheetsMusicDTO.getInstruments().removeIf(childrenEntitiesDTO -> childrenEntitiesDTO.getIndex().equals(s));
                            }
                        }
                    }

                    return result;
                });
            }

            return b;
        });
    }

    private void updateRelatedInstruments(String id, InstrumentsDTO oldInstrumentsDto, InstrumentsDTO instrumentsDTO, AbstractAuthenticationToken abstractAuthenticationToken) {
        if (Objects.equals(oldInstrumentsDto.getName(), instrumentsDTO.getName())) {
            tracksService.alignChildrenInformation(id, abstractAuthenticationToken, stringFilter -> new TracksCriteria().setInstrumentId(stringFilter), (tracksDTO, s) -> {
                boolean result = false;

                if (tracksDTO.getScores() != null) {
                    for (SheetsMusicDTO sheetsMusicDTO : tracksDTO.getScores()) {
                        if (sheetsMusicDTO.getInstruments() != null) {
                            for (ChildrenEntitiesDTO childrenEntitiesDTO : sheetsMusicDTO.getInstruments()) {
                                if (childrenEntitiesDTO.getIndex().equals(s)) {
                                    childrenEntitiesDTO.setName(instrumentsDTO.getName());
                                    result = true;
                                }
                            }
                        }
                    }
                }

                return result;
            });
        }
    }
}
