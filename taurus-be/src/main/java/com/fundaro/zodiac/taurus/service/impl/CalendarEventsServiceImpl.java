package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.resolver.IndexResolver;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Service Implementation for managing {@link CalendarEvents}.
 */
@Service
@Transactional
public class CalendarEventsServiceImpl extends CommonOpenSearchServiceImpl<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsMapper> implements CalendarEventsService {

    public CalendarEventsServiceImpl(OpenSearchService openSearchService, IndexResolver indexResolver, CalendarEventsMapper mapper) {
        super(openSearchService, indexResolver, mapper, CalendarEventsService.class, CalendarEvents.class);
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

    private void applyDefaultEndDate(CalendarEventsDTO dto) {
        if (dto.getEndDate() == null && dto.getStartDate() != null) {
            dto.setEndDate(new Date(dto.getStartDate().getTime() + 3_600_000L));
        }
    }

    @Override
    protected List<Query> getQueries(CalendarEventsCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.dateFilterToQuery("start_date", criteria.getStartDate()));
        queries.addAll(Converter.dateFilterToQuery("end_date", criteria.getEndDate()));
        queries.addAll(Converter.stringFilterToQuery("location.keyword", criteria.getLocation()));
        queries.addAll(Converter.generalFilterToQuery("state.keyword", criteria.getState()));
        return queries;
    }
}
