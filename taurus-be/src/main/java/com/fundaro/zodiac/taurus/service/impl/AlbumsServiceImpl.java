package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service Implementation for managing {@link Albums}.
 */
@Service
@Transactional
public class AlbumsServiceImpl extends CommonOpenSearchServiceImpl<Albums, AlbumsDTO, AlbumsCriteria, AlbumsMapper> implements AlbumsService {

    public AlbumsServiceImpl(OpenSearchService openSearchService, AlbumsMapper albumsMapper) {
        super(openSearchService, albumsMapper, AlbumsService.class, Albums.class, "Albums");
    }

    @Override
    protected List<Query> getQueries(AlbumsCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.dateFilterToQuery("date", criteria.getDate()));
        queries.addAll(Converter.stringFilterToQuery("tracks.name", criteria.getTrackName()));
        queries.addAll(Converter.stringFilterToQuery("tracks.index", criteria.getTrackId()));

        return queries;
    }
}
