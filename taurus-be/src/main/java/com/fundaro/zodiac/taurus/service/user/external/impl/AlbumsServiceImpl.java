package com.fundaro.zodiac.taurus.service.user.external.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import com.fundaro.zodiac.taurus.service.user.TracksService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service Implementation of ROLE_USER_EXTERNAL for managing {@link Albums}.
 * Restricts visibility to PUBLIC state only.
 */
@Service("ExternalPermissionsAlbumsService")
@Transactional
public class AlbumsServiceImpl extends com.fundaro.zodiac.taurus.service.user.impl.AlbumsServiceImpl {

    public AlbumsServiceImpl(
        OpenSearchService openSearchService,
        IndexResolver indexResolver,
        AlbumsMapper albumsMapper,
        @Qualifier("ExternalPermissionsTracksService") TracksService tracksService
    ) {
        super(openSearchService, indexResolver, albumsMapper, tracksService);
    }

    @Override
    protected List<StateEnum> getVisibleStates() {
        return List.of(StateEnum.PUBLIC);
    }
}
