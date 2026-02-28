package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import com.fundaro.zodiac.taurus.service.user.AlbumsService;
import com.fundaro.zodiac.taurus.service.user.TracksService;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tech.jhipster.service.filter.StringFilter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service Implementation of ROLE_USER for managing {@link Albums}.
 */
@Service("LowPermissionsAlbumsService")
@Transactional
public class AlbumsServiceImpl extends CommonOpenSearchServiceImpl<Albums, AlbumsDTO, AlbumsCriteria, AlbumsMapper> implements AlbumsService {

    private final TracksService tracksService;

    public AlbumsServiceImpl(OpenSearchService openSearchService, AlbumsMapper albumsMapper, TracksService tracksService) {
        super(openSearchService, albumsMapper, AlbumsService.class, Albums.class);
        this.tracksService = tracksService;
    }

    @Override
    public Mono<AlbumsDTO> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.findOne(id, abstractAuthenticationToken).flatMap(albumsDTO -> {
            if (albumsDTO == null || !(Objects.equals(albumsDTO.getState(), StateEnum.COMPLETE) || Objects.equals(albumsDTO.getState(), StateEnum.PUBLIC))) {
                return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Albums.class.getSimpleName(), "id.notFound"));
            }

            if (!albumsDTO.getTracks().isEmpty()) {
                TracksCriteria tracksCriteria = new TracksCriteria();
                StringFilter idFilter = new StringFilter();
                idFilter.setIn(albumsDTO.getTracks().stream().map(ChildrenEntitiesDTO::getIndex).toList());
                tracksCriteria.setId(idFilter);

                return tracksService.findEntitiesByCriteria(tracksCriteria, Pageable.ofSize(albumsDTO.getTracks().size()), abstractAuthenticationToken).map(tracksDTOS -> {
                    Set<ChildrenEntitiesDTO> finalList = albumsDTO.getTracks().stream()
                        .filter(childrenEntitiesDTO -> tracksDTOS.stream().anyMatch(tracksDTO -> Objects.equals(tracksDTO.getId(), childrenEntitiesDTO.getIndex())))
                        .sorted(Comparator.comparingLong(ChildrenEntitiesDTO::getOrder))
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                    albumsDTO.setTracks(finalList);

                    return albumsDTO;
                });
            }

            return Mono.just(albumsDTO);
        });
    }

    @Override
    protected List<Query> getQueries(AlbumsCriteria criteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        List<Query> queries = super.getQueries(criteria, abstractAuthenticationToken);
        queries.addAll(Converter.dateFilterToQuery("date", criteria.getDate()));
        queries.addAll(Converter.stringFilterToQuery("tracks.name.keyword", criteria.getTrackName()));
        queries.addAll(Converter.stringFilterToQuery("tracks.index.keyword", criteria.getTrackId()));

        StateFilter stateFilter = new StateFilter();
        stateFilter.setIn(List.of(new StateEnum[]{StateEnum.COMPLETE, StateEnum.PUBLIC}));
        queries.addAll(Converter.generalFilterToQuery("state.keyword", stateFilter));

        return queries;
    }
}
