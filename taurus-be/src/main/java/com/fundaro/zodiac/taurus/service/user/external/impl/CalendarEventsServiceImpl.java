package com.fundaro.zodiac.taurus.service.user.external.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service Implementation of ROLE_USER_EXTERNAL for managing {@link CalendarEvents}.
 * Restricts visibility to PUBLIC state only.
 */
@Service("ExternalPermissionsCalendarEventsService")
@Transactional
public class CalendarEventsServiceImpl extends com.fundaro.zodiac.taurus.service.user.impl.CalendarEventsServiceImpl {

    public CalendarEventsServiceImpl(
        OpenSearchService openSearchService,
        IndexResolver indexResolver,
        CalendarEventsMapper mapper,
        com.fundaro.zodiac.taurus.service.CalendarEventsService adminCalendarEventsService,
        EventReminderProducer eventReminderProducer
    ) {
        super(openSearchService, indexResolver, mapper, adminCalendarEventsService, eventReminderProducer);
    }

    @Override
    protected List<StateEnum> getVisibleStates() {
        return List.of(StateEnum.PUBLIC);
    }
}
