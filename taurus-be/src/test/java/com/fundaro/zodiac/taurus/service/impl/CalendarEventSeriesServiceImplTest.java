package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.CalendarEventSeries;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceFrequency;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.repository.CalendarEventSeriesRepository;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.service.RecurringEventGenerator;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesRequest;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceEndDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceRuleDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CalendarEventSeriesServiceImplTest {

    @Test
    void propagatesFromTheSourceOccurrenceWithoutChangingEarlierOccurrences() {
        CalendarEventSeriesRepository seriesRepository = mock(CalendarEventSeriesRepository.class);
        CalendarEventsRepository eventRepository = mock(CalendarEventsRepository.class);
        CalendarEventsMapper eventMapper = mock(CalendarEventsMapper.class);
        EventReminderProducer reminderProducer = mock(EventReminderProducer.class);

        Instant start = Instant.now().plusSeconds(86_400);
        CalendarEventSeries series = new CalendarEventSeries();
        series.setId(10L);
        series.setTimeZone("UTC");

        CalendarEvents preceding = occurrence(series, 19L, start, 1, false);
        CalendarEvents occurrence = new CalendarEvents();
        occurrence.setId(20L);
        occurrence.setDeleted(false);
        occurrence.setSeries(series);
        occurrence.setName("Nome precedente");
        occurrence.setState(StateEnum.DRAFT);
        occurrence.setStartDate(Date.from(start.plusSeconds(86_400)));
        occurrence.setEndDate(Date.from(start.plusSeconds(93_600)));
        occurrence.setOriginalStartDate(Date.from(start.plusSeconds(86_400)));
        occurrence.setSeriesSequence(2);
        occurrence.setSeriesException(true);
        CalendarEvents following = occurrence(series, 21L, start.plusSeconds(172_800), 3, false);

        when(seriesRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(series));
        when(eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(10L)).thenReturn(List.of(preceding, occurrence, following));
        when(eventMapper.toDto(occurrence)).thenReturn(new CalendarEventsDTO());

        CalendarEventSeriesServiceImpl service = new CalendarEventSeriesServiceImpl(
            seriesRepository,
            eventRepository,
            eventMapper,
            new RecurringEventGenerator(),
            mock(TenantTimeZoneService.class),
            reminderProducer,
            500
        );

        CalendarEventsDTO template = new CalendarEventsDTO();
        template.setName("Nuovo nome");
        template.setState(StateEnum.COMPLETE);
        template.setLocation("Nuovo luogo");
        template.setFee(new BigDecimal("125.50"));
        template.setReminderMinutes(30);
        template.setStartDate(Date.from(start));
        template.setEndDate(Date.from(start.plusSeconds(7_200)));

        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(RecurrenceEndType.COUNT);
        end.setCount(3);
        RecurrenceRuleDTO recurrence = new RecurrenceRuleDTO();
        recurrence.setFrequency(RecurrenceFrequency.DAILY);
        recurrence.setInterval(1);
        recurrence.setEnd(end);

        CalendarEventSeriesRequest request = new CalendarEventSeriesRequest();
        request.setSourceOccurrenceId(occurrence.getId());
        request.setTemplate(template);
        request.setRecurrence(recurrence);

        service.update(series.getId(), request, authentication());

        assertThat(preceding.getName()).isEqualTo("Nome precedente");
        assertThat(preceding.getState()).isEqualTo(StateEnum.DRAFT);
        assertThat(occurrence.getName()).isEqualTo("Nuovo nome");
        assertThat(occurrence.getState()).isEqualTo(StateEnum.COMPLETE);
        assertThat(occurrence.getLocation()).isEqualTo("Nuovo luogo");
        assertThat(occurrence.getFee()).isEqualByComparingTo("125.50");
        assertThat(occurrence.getReminderMinutes()).isEqualTo(30);
        assertThat(occurrence.getSeriesException()).isFalse();
        assertThat(following.getName()).isEqualTo("Nuovo nome");
        assertThat(following.getState()).isEqualTo(StateEnum.COMPLETE);
    }

    private static CalendarEvents occurrence(
        CalendarEventSeries series,
        Long id,
        Instant start,
        int sequence,
        boolean exception
    ) {
        CalendarEvents occurrence = new CalendarEvents();
        occurrence.setId(id);
        occurrence.setDeleted(false);
        occurrence.setSeries(series);
        occurrence.setName("Nome precedente");
        occurrence.setState(StateEnum.DRAFT);
        occurrence.setStartDate(Date.from(start));
        occurrence.setEndDate(Date.from(start.plusSeconds(7_200)));
        occurrence.setOriginalStartDate(Date.from(start));
        occurrence.setSeriesSequence(sequence);
        occurrence.setSeriesException(exception);
        return occurrence;
    }

    private static AbstractAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("admin-1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
