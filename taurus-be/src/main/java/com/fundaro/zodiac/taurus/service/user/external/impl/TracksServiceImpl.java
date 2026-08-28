package com.fundaro.zodiac.taurus.service.user.external.impl;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.UsersService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service Implementation of ROLE_USER_EXTERNAL for managing tracks.
 * Restricts visibility to PUBLIC state and filters sheet music by the user's instruments.
 */
@Service("ExternalPermissionsTracksService")
@Transactional
public class TracksServiceImpl extends com.fundaro.zodiac.taurus.service.user.impl.TracksServiceImpl {

    public TracksServiceImpl(
        com.fundaro.zodiac.taurus.service.TracksService tracksService,
        UsersService usersService
    ) {
        super(tracksService, usersService);
    }

    @Override
    protected List<StateEnum> getVisibleStates() {
        return List.of(StateEnum.PUBLIC);
    }
}
