package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.user.TracksService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("LowPermissionsTracksService")
public class TracksServiceImpl implements TracksService {
    private final com.fundaro.zodiac.taurus.service.TracksService delegate;
    private final UsersService usersService;

    public TracksServiceImpl(com.fundaro.zodiac.taurus.service.TracksService delegate, UsersService usersService) {
        this.delegate = delegate;
        this.usersService = usersService;
    }

    protected List<StateEnum> getVisibleStates() {
        return List.of(StateEnum.COMPLETE, StateEnum.PUBLIC);
    }

    protected Page<TracksDTO> findEntitiesWithoutInstrumentFilter(TracksCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) {
        StateFilter state = new StateFilter();
        state.setIn(getVisibleStates());
        criteria.setState(state);
        return delegate.findEntitiesByCriteria(criteria, pageable, token);
    }

    protected Optional<TracksDTO> findOneWithoutInstrumentFilter(Long id, AbstractAuthenticationToken token) {
        return delegate.findOne(id, token).filter(track -> getVisibleStates().contains(track.getState()));
    }

    @Override
    public Page<TracksDTO> findEntitiesByCriteria(TracksCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) {
        UsersDTO user = usersService.findMe(token).orElse(new UsersDTO());
        Page<TracksDTO> page = findEntitiesWithoutInstrumentFilter(criteria, pageable, token);
        page.getContent().forEach(track -> filterScores(track, user));
        return page;
    }

    @Override
    public Optional<TracksDTO> findOne(Long id, AbstractAuthenticationToken token) {
        TracksDTO track = findOneWithoutInstrumentFilter(id, token)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Tracks.class.getSimpleName(), "id.notFound"));
        UsersDTO user = usersService.findMe(token).orElse(new UsersDTO());
        filterScores(track, user);
        return Optional.of(track);
    }

    private static void filterScores(TracksDTO track, UsersDTO user) {
        if (track.getScores() == null) return;
        Set<Long> assignedInstrumentIds = Optional.ofNullable(user.getInstruments()).orElse(Collections.emptySet()).stream()
            .filter(Objects::nonNull)
            .map(instrument -> instrument.getIndex())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        track.setScores(track.getScores().stream().filter(Objects::nonNull)
            .filter(score -> score.getInstruments() != null && score.getInstruments().stream()
                .filter(Objects::nonNull)
                .map(instrument -> instrument.getIndex())
                .anyMatch(assignedInstrumentIds::contains))
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
