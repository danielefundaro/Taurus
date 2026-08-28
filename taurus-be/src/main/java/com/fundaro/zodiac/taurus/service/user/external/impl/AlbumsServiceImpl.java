package com.fundaro.zodiac.taurus.service.user.external.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
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
        com.fundaro.zodiac.taurus.service.AlbumsService albumsService
    ) {
        super(albumsService);
    }

    @Override
    protected List<StateEnum> getVisibleStates() {
        return List.of(StateEnum.PUBLIC);
    }
}
