package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventPresentUserDTO;
import com.fundaro.zodiac.taurus.service.dto.EventUserEntryDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing {@link CalendarEvents}.
 */
@Service
@Transactional
public class CalendarEventsServiceImpl extends CommonOpenSearchServiceImpl<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsMapper> implements CalendarEventsService {

    private final EventReminderProducer eventReminderProducer;

    public CalendarEventsServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, CalendarEventsMapper mapper, EventReminderProducer eventReminderProducer) {
        super(openSearchService, indexResolver, mapper, CalendarEventsService.class, CalendarEvents.class);
        this.eventReminderProducer = eventReminderProducer;
    }

    @Override
    public CalendarEventsDTO save(CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        applyDefaultEndDate(dto);
        return super.save(dto, token);
    }

    @Override
    public CalendarEventsDTO update(String id, CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        applyDefaultEndDate(dto);
        return super.update(id, dto, token);
    }

    @Override
    public CalendarEventsDTO partialUpdate(String id, CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        applyDefaultEndDate(dto);
        return super.partialUpdate(id, dto, token);
    }

    @Override
    protected List<Query> getQueries(CalendarEventsCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.dateFilterToQuery("start_date", criteria.getStartDate()));
        queries.addAll(Converter.dateFilterToQuery("end_date", criteria.getEndDate()));
        queries.addAll(Converter.stringFilterToQuery("location.keyword", criteria.getLocation()));
        queries.addAll(Converter.generalFilterToQuery("state.keyword", criteria.getState()));
        queries.addAll(Converter.stringFilterToQuery("present_users.index", criteria.getPresentUserId()));
        return queries;
    }

    @Override
    public CalendarEventsDTO setAvailability(String eventId, boolean available, AbstractAuthenticationToken token) {
        CalendarEventsDTO dto = findOne(eventId, token)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", CalendarEvents.class.getSimpleName(), "id.notFound"));

        String userId = SecurityUtils.getUserIdFromAuthentication(token);
        Date now = new Date();

        if (dto.getAvailableUsers() != null) {
            dto.setAvailableUsers(dto.getAvailableUsers().stream()
                .filter(e -> !userId.equals(e.getIndex()))
                .collect(Collectors.toList()));
        }
        if (dto.getUnavailableUsers() != null) {
            dto.setUnavailableUsers(dto.getUnavailableUsers().stream()
                .filter(e -> !userId.equals(e.getIndex()))
                .collect(Collectors.toList()));
        }

        EventUserEntryDTO entry = new EventUserEntryDTO();
        entry.setIndex(userId);
        entry.setName(SecurityUtils.getFirstNameFromAuthentication(token));
        entry.setLastName(SecurityUtils.getLastNameFromAuthentication(token));
        entry.setResponseDate(now);

        if (available) {
            if (dto.getAvailableUsers() == null) dto.setAvailableUsers(new ArrayList<>());
            dto.getAvailableUsers().add(entry);
        } else {
            if (dto.getUnavailableUsers() == null) dto.setUnavailableUsers(new ArrayList<>());
            dto.getUnavailableUsers().add(entry);
        }

        CalendarEventsDTO result = update(eventId, dto, token);

        if (available) {
            String tenantCode = SecurityUtils.getTenantIdFromAuthentication(token);
            eventReminderProducer.scheduleIfNeeded(result, userId, tenantCode, token);
        }

        return result;
    }

    @Override
    public CalendarEventsDTO cancelAvailability(String eventId, AbstractAuthenticationToken token) {
        CalendarEventsDTO dto = findOne(eventId, token)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", CalendarEvents.class.getSimpleName(), "id.notFound"));

        String userId = SecurityUtils.getUserIdFromAuthentication(token);

        if (dto.getAvailableUsers() != null) {
            dto.setAvailableUsers(dto.getAvailableUsers().stream()
                .filter(e -> !userId.equals(e.getIndex()))
                .collect(Collectors.toList()));
        }
        if (dto.getUnavailableUsers() != null) {
            dto.setUnavailableUsers(dto.getUnavailableUsers().stream()
                .filter(e -> !userId.equals(e.getIndex()))
                .collect(Collectors.toList()));
        }

        return update(eventId, dto, token);
    }

    @Override
    public CalendarEventsDTO setPresentUsers(String eventId, List<EventPresentUserDTO> presentUsers, AbstractAuthenticationToken token) {
        CalendarEventsDTO dto = findOne(eventId, token)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", CalendarEvents.class.getSimpleName(), "id.notFound"));
        dto.setPresentUsers(presentUsers);
        return update(eventId, dto, token);
    }

    private void applyDefaultEndDate(CalendarEventsDTO dto) {
        if (dto.getEndDate() == null && dto.getStartDate() != null) {
            dto.setEndDate(new Date(dto.getStartDate().getTime() + 3_600_000L));
        }
    }
}
