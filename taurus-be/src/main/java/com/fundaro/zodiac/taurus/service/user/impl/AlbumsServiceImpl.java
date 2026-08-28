package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.criteria.AlbumsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.user.AlbumsService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service("LowPermissionsAlbumsService")
public class AlbumsServiceImpl implements AlbumsService {
    private final com.fundaro.zodiac.taurus.service.AlbumsService delegate;

    public AlbumsServiceImpl(com.fundaro.zodiac.taurus.service.AlbumsService delegate) { this.delegate = delegate; }

    protected List<StateEnum> getVisibleStates() { return List.of(StateEnum.COMPLETE, StateEnum.PUBLIC); }

    @Override
    public Page<AlbumsDTO> findEntitiesByCriteria(AlbumsCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) {
        StateFilter state = new StateFilter(); state.setIn(getVisibleStates()); criteria.setState(state);
        return delegate.findEntitiesByCriteria(criteria, pageable, token);
    }

    @Override
    public Optional<AlbumsDTO> findOne(Long id, AbstractAuthenticationToken token) {
        AlbumsDTO dto = delegate.findOne(id, token).filter(album -> getVisibleStates().contains(album.getState()))
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Albums.class.getSimpleName(), "id.notFound"));
        return Optional.of(dto);
    }
}
