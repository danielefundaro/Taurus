package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import com.fundaro.zodiac.taurus.service.user.AlbumsService;
import com.fundaro.zodiac.taurus.utils.Converter;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Service Implementation of ROLE_USER for managing {@link Albums}.
 */
@Service("LowPermissionsAlbumsService")
@Transactional
public class AlbumsServiceImpl extends CommonOpenSearchServiceImpl<Albums, AlbumsDTO, AlbumsCriteria, AlbumsMapper> implements AlbumsService {

    public AlbumsServiceImpl(OpenSearchService openSearchService, AlbumsMapper albumsMapper) {
        super(openSearchService, albumsMapper, AlbumsService.class, Albums.class);
    }

    @Override
    protected Albums getById(String id, AbstractAuthenticationToken abstractAuthenticationToken) throws IOException {
        Albums albums = super.getById(id, abstractAuthenticationToken);

        if (albums == null || !(Objects.equals(albums.getState(), StateEnum.COMPLETE) || Objects.equals(albums.getState(), StateEnum.PUBLIC))) {
            throw new IOException();
        }

        return albums;
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
