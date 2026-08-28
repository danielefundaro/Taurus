package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TracksServiceImplTest {

    private final AbstractAuthenticationToken token = new TestingAuthenticationToken("user", "password");

    @Test
    void filtersSheetMusicUsingAtLeastOneAssignedInstrumentWithoutHidingTracks() {
        TracksDTO trackX = track(1L, sheetMusic(1L), sheetMusic(3L), sheetMusic(5L));
        TracksDTO trackY = track(2L, sheetMusic(2L), sheetMusic(3L), sheetMusic(7L));
        TracksDTO trackZ = track(3L, sheetMusic(15L), sheetMusic(16L), sheetMusic(17L, 18L));
        UsersDTO user = userWithInstruments(1L, 2L, 3L);
        TracksCriteria criteria = new TracksCriteria();
        Pageable pageable = PageRequest.of(0, 20);
        Page<TracksDTO> page = new PageImpl<>(java.util.List.of(trackX, trackY, trackZ), pageable, 3);
        TracksServiceImpl service = service(Optional.of(user), page, Optional.empty());

        Page<TracksDTO> result = service.findEntitiesByCriteria(criteria, pageable, token);

        assertThat(result.getContent()).containsExactly(trackX, trackY, trackZ);
        assertThat(instrumentIds(trackX)).containsExactlyInAnyOrder(1L, 3L);
        assertThat(instrumentIds(trackY)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(trackZ.getScores()).isEmpty();
        assertThat(criteria.getState().getIn()).containsExactlyInAnyOrder(StateEnum.COMPLETE, StateEnum.PUBLIC);
    }

    @Test
    void returnsTracksWithEmptySheetMusicWhenUserHasNoInstruments() {
        TracksDTO track = track(1L, sheetMusic(1L));
        TracksCriteria criteria = new TracksCriteria();
        Pageable pageable = PageRequest.of(0, 20);
        Page<TracksDTO> page = new PageImpl<>(java.util.List.of(track), pageable, 1);
        TracksServiceImpl service = service(Optional.of(new UsersDTO()), page, Optional.empty());

        Page<TracksDTO> result = service.findEntitiesByCriteria(criteria, pageable, token);

        assertThat(result.getContent()).containsExactly(track);
        assertThat(track.getScores()).isEmpty();
    }

    @Test
    void filtersSheetMusicOnTrackDetail() {
        TracksDTO track = track(1L, sheetMusic(1L), sheetMusic(5L), sheetMusic(7L));
        track.setState(StateEnum.PUBLIC);
        TracksServiceImpl service = service(Optional.of(userWithInstruments(1L, 5L)), Page.empty(), Optional.of(track));

        Optional<TracksDTO> result = service.findOne(1L, token);

        assertThat(result).contains(track);
        assertThat(instrumentIds(track)).containsExactlyInAnyOrder(1L, 5L);
    }

    private static TracksServiceImpl service(Optional<UsersDTO> user, Page<TracksDTO> page, Optional<TracksDTO> detail) {
        com.fundaro.zodiac.taurus.service.TracksService delegate = proxy(
            com.fundaro.zodiac.taurus.service.TracksService.class,
            (methodName, args) -> switch (methodName) {
                case "findEntitiesByCriteria" -> page;
                case "findOne" -> detail;
                default -> null;
            }
        );
        UsersService usersService = proxy(UsersService.class, (methodName, args) -> "findMe".equals(methodName) ? user : null);
        return new TracksServiceImpl(delegate, usersService);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> invocation.call(method.getName(), args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String methodName, Object[] args);
    }

    private static TracksDTO track(Long id, SheetsMusicDTO... scores) {
        TracksDTO track = new TracksDTO();
        track.setId(id);
        track.setScores(new java.util.LinkedHashSet<>(java.util.List.of(scores)));
        return track;
    }

    private static SheetsMusicDTO sheetMusic(Long... instrumentIds) {
        SheetsMusicDTO sheetMusic = new SheetsMusicDTO();
        sheetMusic.setInstruments(java.util.Arrays.stream(instrumentIds).map(TracksServiceImplTest::instrument).collect(java.util.stream.Collectors.toSet()));
        return sheetMusic;
    }

    private static UsersDTO userWithInstruments(Long... instrumentIds) {
        UsersDTO user = new UsersDTO();
        user.setInstruments(java.util.Arrays.stream(instrumentIds).map(TracksServiceImplTest::instrument).collect(java.util.stream.Collectors.toSet()));
        return user;
    }

    private static ChildrenEntitiesDTO instrument(Long id) {
        ChildrenEntitiesDTO instrument = new ChildrenEntitiesDTO();
        instrument.setIndex(id);
        return instrument;
    }

    private static Set<Long> instrumentIds(TracksDTO track) {
        return track.getScores().stream()
            .flatMap(sheet -> sheet.getInstruments().stream())
            .map(ChildrenEntitiesDTO::getIndex)
            .collect(java.util.stream.Collectors.toSet());
    }
}
