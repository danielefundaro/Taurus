package com.fundaro.zodiac.taurus.service.user.external.impl;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service Implementation of ROLE_USER_EXTERNAL for managing {@link Tracks}.
 * Restricts visibility to PUBLIC state only; skips instrument-based filtering.
 */
@Service("ExternalPermissionsTracksService")
@Transactional
public class TracksServiceImpl extends com.fundaro.zodiac.taurus.service.user.impl.TracksServiceImpl {

    public TracksServiceImpl(
        OpenSearchService openSearchService,
        IndexResolver indexResolver,
        TracksMapper mapper,
        UsersService usersService
    ) {
        super(openSearchService, indexResolver, mapper, usersService);
    }

    @Override
    protected List<StateEnum> getVisibleStates() {
        return List.of(StateEnum.PUBLIC);
    }

    @Override
    public Page<TracksDTO> findEntitiesByCriteria(TracksCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) {
        return findEntitiesWithoutInstrumentFilter(criteria, pageable, token);
    }

    @Override
    public Optional<TracksDTO> findOne(String id, AbstractAuthenticationToken token) {
        TracksDTO tracksDTO = findOneWithoutInstrumentFilter(id, token)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Tracks.class.getSimpleName(), "id.notFound"));
        if (!getVisibleStates().contains(tracksDTO.getState())) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Tracks.class.getSimpleName(), "id.notFound");
        }
        return Optional.of(tracksDTO);
    }
}
