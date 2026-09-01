package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesPreviewDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public interface CalendarEventSeriesService {
    CalendarEventSeriesPreviewDTO preview(CalendarEventSeriesRequest request);
    CalendarEventSeriesDTO create(CalendarEventSeriesRequest request, AbstractAuthenticationToken token);
    CalendarEventSeriesDTO findOne(Long id);
    CalendarEventSeriesDTO update(Long id, CalendarEventSeriesRequest request, AbstractAuthenticationToken token);
    CalendarEventSeriesDTO deleteFuture(Long id, AbstractAuthenticationToken token);
    CalendarEventSeriesDTO restoreOccurrence(Long seriesId, Long eventId, AbstractAuthenticationToken token);
}
