package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.user.CalendarEventsService;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service("LowPermissionsCalendarEventsService")
public class CalendarEventsServiceImpl implements CalendarEventsService {
    private final com.fundaro.zodiac.taurus.service.CalendarEventsService delegate;
    public CalendarEventsServiceImpl(com.fundaro.zodiac.taurus.service.CalendarEventsService delegate) { this.delegate = delegate; }
    protected List<StateEnum> getVisibleStates() { return List.of(StateEnum.COMPLETE, StateEnum.PUBLIC); }
    public Optional<CalendarEventsDTO> findOne(Long id, AbstractAuthenticationToken token) { return delegate.findOne(id, token).filter(dto -> getVisibleStates().contains(dto.getState())).map(this::mask); }
    public Page<CalendarEventsDTO> findEntitiesByCriteria(CalendarEventsCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) { StateFilter state = new StateFilter(); state.setIn(getVisibleStates()); criteria.setState(state); return delegate.findEntitiesByCriteria(criteria, pageable, token).map(this::mask); }
    public CalendarEventsDTO setAvailability(Long eventId, boolean available, AbstractAuthenticationToken token) { return mask(delegate.setAvailability(eventId, available, token)); }
    public CalendarEventsDTO cancelAvailability(Long eventId, AbstractAuthenticationToken token) { return mask(delegate.cancelAvailability(eventId, token)); }
    private CalendarEventsDTO mask(CalendarEventsDTO dto) { dto.setFee(null); dto.setCosts(null); return dto; }
}
