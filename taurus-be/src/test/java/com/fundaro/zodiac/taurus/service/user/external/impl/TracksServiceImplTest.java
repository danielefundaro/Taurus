package com.fundaro.zodiac.taurus.service.user.external.impl;

import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import org.junit.jupiter.api.Test;
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

    @Test
    void filtersExternalUserSheetMusicAndKeepsPublicTracksVisible() {
        AbstractAuthenticationToken token = new TestingAuthenticationToken("external-user", "password");
        TracksCriteria criteria = new TracksCriteria();
        Pageable pageable = PageRequest.of(0, 20);
        TracksDTO track = new TracksDTO();
        SheetsMusicDTO matchingSheet = sheetMusic(1L, 5L);
        track.setScores(Set.of(matchingSheet, sheetMusic(7L)));
        UsersDTO user = new UsersDTO();
        user.setInstruments(Set.of(instrument(1L)));
        PageImpl<TracksDTO> page = new PageImpl<>(java.util.List.of(track), pageable, 1);
        com.fundaro.zodiac.taurus.service.TracksService delegate = proxy(
            com.fundaro.zodiac.taurus.service.TracksService.class,
            (methodName, args) -> "findEntitiesByCriteria".equals(methodName) ? page : null
        );
        UsersService usersService = proxy(UsersService.class, (methodName, args) -> "findMe".equals(methodName) ? Optional.of(user) : null);
        TracksServiceImpl service = new TracksServiceImpl(delegate, usersService);

        service.findEntitiesByCriteria(criteria, pageable, token);

        assertThat(track.getScores()).containsExactly(matchingSheet);
        assertThat(criteria.getState().getIn()).containsExactly(StateEnum.PUBLIC);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> invocation.call(method.getName(), args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String methodName, Object[] args);
    }

    private static SheetsMusicDTO sheetMusic(Long... instrumentIds) {
        SheetsMusicDTO sheetMusic = new SheetsMusicDTO();
        sheetMusic.setInstruments(java.util.Arrays.stream(instrumentIds).map(TracksServiceImplTest::instrument).collect(java.util.stream.Collectors.toSet()));
        return sheetMusic;
    }

    private static ChildrenEntitiesDTO instrument(Long id) {
        ChildrenEntitiesDTO instrument = new ChildrenEntitiesDTO();
        instrument.setIndex(id);
        return instrument;
    }
}
