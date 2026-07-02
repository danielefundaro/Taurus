package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventUserEntryDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.service.user.CalendarEventsService;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service Implementation of ROLE_USER for managing {@link CalendarEvents}.
 */
@Service("LowPermissionsCalendarEventsService")
@Transactional
public class CalendarEventsServiceImpl
    extends CommonOpenSearchServiceImpl<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsMapper>
    implements CalendarEventsService {

    private final com.fundaro.zodiac.taurus.service.CalendarEventsService adminCalendarEventsService;

    public CalendarEventsServiceImpl(
        OpenSearchService openSearchService,
        IndexResolver indexResolver,
        CalendarEventsMapper mapper,
        com.fundaro.zodiac.taurus.service.CalendarEventsService adminCalendarEventsService
    ) {
        super(openSearchService, indexResolver, mapper, CalendarEventsService.class, CalendarEvents.class);
        this.adminCalendarEventsService = adminCalendarEventsService;
    }

    @Override
    public Optional<CalendarEventsDTO> findOne(String id, AbstractAuthenticationToken token) {
        return super.findOne(id, token)
            .filter(dto -> dto.getState() == StateEnum.COMPLETE || dto.getState() == StateEnum.PUBLIC)
            .map(this::maskSensitiveFields);
    }

    @Override
    public Page<CalendarEventsDTO> findEntitiesByCriteria(CalendarEventsCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) {
        return super.findEntitiesByCriteria(criteria, pageable, token)
            .map(this::maskSensitiveFields);
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

        return adminCalendarEventsService.update(eventId, dto, token);
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

        return adminCalendarEventsService.update(eventId, dto, token);
    }

    @Override
    protected List<Query> getQueries(CalendarEventsCriteria criteria, AbstractAuthenticationToken token) {
        List<Query> queries = super.getQueries(criteria, token);

        StateFilter stateFilter = new StateFilter();
        stateFilter.setIn(List.of(StateEnum.COMPLETE, StateEnum.PUBLIC));
        queries.addAll(Converter.generalFilterToQuery("state.keyword", stateFilter));
        queries.addAll(Converter.dateFilterToQuery("start_date", criteria.getStartDate()));
        queries.addAll(Converter.dateFilterToQuery("end_date", criteria.getEndDate()));
        queries.addAll(Converter.stringFilterToQuery("location.keyword", criteria.getLocation()));

        return queries;
    }

    private CalendarEventsDTO maskSensitiveFields(CalendarEventsDTO dto) {
        dto.setFee(null);
        dto.setCosts(null);
        return dto;
    }
}
