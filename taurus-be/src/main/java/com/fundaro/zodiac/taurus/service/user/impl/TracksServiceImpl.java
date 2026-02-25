package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
import com.fundaro.zodiac.taurus.service.user.TracksService;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tech.jhipster.service.filter.StringFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing {@link Tracks}.
 */
@Service("LowPermissionsTracksService")
@Transactional
public class TracksServiceImpl extends CommonOpenSearchServiceImpl<Tracks, TracksDTO, TracksCriteria, TracksMapper> implements TracksService {

    private final UsersService usersService;

    public TracksServiceImpl(OpenSearchService openSearchService, TracksMapper mapper, UsersService usersService) {
        super(openSearchService, mapper, TracksService.class, Tracks.class);
        this.usersService = usersService;
    }

    @Override
    public Mono<Page<TracksDTO>> findEntitiesByCriteria(TracksCriteria criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken) {
        return usersService.findMe(abstractAuthenticationToken).flatMap(usersDTO -> {
            if (usersDTO.getInstruments() == null || usersDTO.getInstruments().isEmpty()) {
                return Mono.just(new PageImpl<>(new ArrayList<>(), pageable, 0L));
            }

            // Add mandatory instruments filter
            StringFilter stringFilter = new StringFilter();
            stringFilter.setIn(usersDTO.getInstruments().stream().map(ChildrenEntitiesDTO::getIndex).toList());
            criteria.setInstrumentId(stringFilter);

            return super.findEntitiesByCriteria(criteria, pageable, abstractAuthenticationToken);
        });
    }

    @Override
    public Mono<TracksDTO> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.findOne(id, abstractAuthenticationToken).flatMap(tracksDTO -> usersService.findMe(abstractAuthenticationToken).flatMap(usersDTO -> {
            if (tracksDTO == null || !(Objects.equals(tracksDTO.getState(), StateEnum.COMPLETE) || Objects.equals(tracksDTO.getState(), StateEnum.PUBLIC))) {
                return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Tracks.class.getSimpleName(), "id.notFound"));
            }

            if (usersDTO.getInstruments() == null || usersDTO.getInstruments().isEmpty()) {
                return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Tracks.class.getSimpleName(), "id.notFound"));
            }

            tracksDTO.setScores(tracksDTO.getScores().stream().filter(Objects::nonNull)
                .filter(sheetsMusicDTO -> Objects.nonNull(sheetsMusicDTO.getInstruments()) && sheetsMusicDTO.getInstruments().stream()
                    .anyMatch(childrenEntities -> usersDTO.getInstruments().stream()
                        .anyMatch(ce -> ce.getIndex().equalsIgnoreCase(childrenEntities.getIndex()))
                    )
                ).collect(Collectors.toSet()));

            return Mono.just(tracksDTO);
        }));
    }

    @Override
    protected List<Query> getQueries(TracksCriteria criteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        List<Query> queries = super.getQueries(criteria, abstractAuthenticationToken);
        queries.addAll(Converter.stringFilterToQuery("composer.keyword", criteria.getComposer()));
        queries.addAll(Converter.stringFilterToQuery("arranger.keyword", criteria.getArranger()));
        queries.addAll(Converter.stringFilterToQuery("tempo.keyword", criteria.getTempo()));
        queries.addAll(Converter.stringFilterToQuery("tone.keyword", criteria.getTone()));
        queries.addAll(Converter.stringFilterToQuery("type.keyword", criteria.getType()));
        queries.addAll(Converter.stringFilterToQuery("scores.media.index.keyword", criteria.getMediaId()));
        queries.addAll(Converter.stringFilterToQuery("scores.instruments.index.keyword", criteria.getInstrumentId()));

        StateFilter stateFilter = new StateFilter();
        stateFilter.setIn(List.of(new StateEnum[]{StateEnum.COMPLETE, StateEnum.PUBLIC}));
        queries.addAll(Converter.generalFilterToQuery("state.keyword", stateFilter));

        return queries;
    }
}
