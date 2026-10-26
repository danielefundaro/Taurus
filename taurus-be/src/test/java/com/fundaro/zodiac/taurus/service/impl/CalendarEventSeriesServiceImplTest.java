package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEventSeries;
import com.fundaro.zodiac.taurus.domain.CalendarEventSeriesCost;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceFrequency;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.repository.CalendarEventSeriesRepository;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.service.RecurringEventGenerator;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesRequest;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceEndDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceRuleDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarEventSeriesServiceImplTest {

    @Test
    void propagatesFromTheSourceOccurrenceWithoutChangingEarlierOccurrences() {
        CalendarEventSeriesRepository seriesRepository = mock(CalendarEventSeriesRepository.class);
        CalendarEventsRepository eventRepository = mock(CalendarEventsRepository.class);
        CalendarEventsMapper eventMapper = mock(CalendarEventsMapper.class);
        EventReminderProducer reminderProducer = mock(EventReminderProducer.class);
        TenantFeatureService tenantFeatureService = mock(TenantFeatureService.class);
        when(tenantFeatureService.isEnabled(TenantFeature.FINANCE)).thenReturn(true);

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
            tenantFeatureService,
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

    @Test
    void movesTheIdentityOfThePropagatedOccurrencesToTheirNewStart() {
        CalendarEventSeriesRepository seriesRepository = mock(CalendarEventSeriesRepository.class);
        CalendarEventsRepository eventRepository = mock(CalendarEventsRepository.class);
        CalendarEventsMapper eventMapper = mock(CalendarEventsMapper.class);
        TenantFeatureService tenantFeatureService = mock(TenantFeatureService.class);
        when(tenantFeatureService.isEnabled(TenantFeature.FINANCE)).thenReturn(true);

        Instant start = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MINUTES);
        CalendarEventSeries series = new CalendarEventSeries();
        series.setId(10L);
        series.setTimeZone("UTC");
        CalendarEvents preceding = occurrence(series, 19L, start, 1, false);
        CalendarEvents source = occurrence(series, 20L, start.plusSeconds(86_400), 2, false);
        CalendarEvents following = occurrence(series, 21L, start.plusSeconds(172_800), 3, false);
        when(seriesRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(series));
        when(eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(10L)).thenReturn(List.of(preceding, source, following));

        CalendarEventSeriesServiceImpl service = new CalendarEventSeriesServiceImpl(
            seriesRepository,
            eventRepository,
            eventMapper,
            new RecurringEventGenerator(),
            mock(TenantTimeZoneService.class),
            mock(EventReminderProducer.class),
            tenantFeatureService,
            500
        );

        // The second occurrence moved one hour later: the rule of the series moves with it from there on.
        Instant shifted = start.plusSeconds(3_600);
        CalendarEventsDTO template = new CalendarEventsDTO();
        template.setName("Prova");
        template.setStartDate(Date.from(shifted));
        template.setEndDate(Date.from(shifted.plusSeconds(7_200)));
        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(RecurrenceEndType.COUNT);
        end.setCount(3);
        RecurrenceRuleDTO recurrence = new RecurrenceRuleDTO();
        recurrence.setFrequency(RecurrenceFrequency.DAILY);
        recurrence.setInterval(1);
        recurrence.setEnd(end);
        CalendarEventSeriesRequest request = new CalendarEventSeriesRequest();
        request.setSourceOccurrenceId(source.getId());
        request.setTemplate(template);
        request.setRecurrence(recurrence);

        service.update(series.getId(), request, authentication());

        assertThat(preceding.getOriginalStartDate().toInstant()).isEqualTo(start);
        assertThat(source.getStartDate().toInstant()).isEqualTo(shifted.plusSeconds(86_400));
        assertThat(source.getOriginalStartDate().toInstant()).isEqualTo(shifted.plusSeconds(86_400));
        assertThat(following.getOriginalStartDate().toInstant()).isEqualTo(shifted.plusSeconds(172_800));
    }

    @Test
    void preservesStoredEconomicsAndRedactsTheResponseWhenFinanceIsDisabled() {
        CalendarEventSeriesRepository seriesRepository = mock(CalendarEventSeriesRepository.class);
        CalendarEventsRepository eventRepository = mock(CalendarEventsRepository.class);
        CalendarEventsMapper eventMapper = mock(CalendarEventsMapper.class);
        EventReminderProducer reminderProducer = mock(EventReminderProducer.class);
        TenantFeatureService tenantFeatureService = mock(TenantFeatureService.class);
        when(tenantFeatureService.isEnabled(TenantFeature.FINANCE)).thenReturn(false);

        Instant start = Instant.now().plusSeconds(86_400);
        CalendarEventSeries series = new CalendarEventSeries();
        series.setId(10L);
        series.setTimeZone("UTC");
        series.setFee(new BigDecimal("500.00"));
        CalendarEventSeriesCost seriesCost = new CalendarEventSeriesCost();
        seriesCost.setDescription("Costo serie");
        seriesCost.setAmount(new BigDecimal("75.00"));
        series.getCosts().add(seriesCost);

        CalendarEvents occurrence = occurrence(series, 20L, start, 1, false);
        occurrence.setFee(new BigDecimal("250.00"));
        EventCost occurrenceCost = new EventCost();
        occurrenceCost.setDescription("Costo evento");
        occurrenceCost.setAmount(new BigDecimal("25.00"));
        occurrence.getCosts().add(occurrenceCost);

        when(seriesRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(series));
        when(eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(10L)).thenReturn(List.of(occurrence));
        when(eventMapper.toDto(occurrence)).thenReturn(new CalendarEventsDTO());

        CalendarEventSeriesServiceImpl service = new CalendarEventSeriesServiceImpl(
            seriesRepository,
            eventRepository,
            eventMapper,
            new RecurringEventGenerator(),
            mock(TenantTimeZoneService.class),
            reminderProducer,
            tenantFeatureService,
            500
        );

        CalendarEventsDTO template = new CalendarEventsDTO();
        template.setName("Nome aggiornato");
        template.setFee(BigDecimal.ZERO);
        template.setCosts(List.of());
        template.setStartDate(Date.from(start));
        template.setEndDate(Date.from(start.plusSeconds(7_200)));

        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(RecurrenceEndType.COUNT);
        end.setCount(1);
        RecurrenceRuleDTO recurrence = new RecurrenceRuleDTO();
        recurrence.setFrequency(RecurrenceFrequency.DAILY);
        recurrence.setInterval(1);
        recurrence.setEnd(end);

        CalendarEventSeriesRequest request = new CalendarEventSeriesRequest();
        request.setTemplate(template);
        request.setRecurrence(recurrence);

        var result = service.update(series.getId(), request, authentication());

        assertThat(series.getFee()).isEqualByComparingTo("500.00");
        assertThat(series.getCosts()).containsExactly(seriesCost);
        assertThat(occurrence.getFee()).isEqualByComparingTo("250.00");
        assertThat(occurrence.getCosts()).containsExactly(occurrenceCost);
        assertThat(result.getTemplate().getFee()).isNull();
        assertThat(result.getTemplate().getCosts()).isEmpty();
    }

    @Test
    void reportsTheOccurrencesKeptAsExceptionsAndThoseOutsideTheNewRule() {
        CalendarEventSeriesRepository seriesRepository = mock(CalendarEventSeriesRepository.class);
        CalendarEventsRepository eventRepository = mock(CalendarEventsRepository.class);
        CalendarEventsMapper eventMapper = mock(CalendarEventsMapper.class);
        TenantFeatureService tenantFeatureService = mock(TenantFeatureService.class);

        Instant start = Instant.now().plusSeconds(86_400);
        CalendarEventSeries series = new CalendarEventSeries();
        series.setId(30L);
        series.setTimeZone("UTC");

        // La sorgente, una posizione normale e due eccezioni: una dentro la nuova regola, una oltre.
        CalendarEvents source = occurrence(series, 40L, start, 1, false);
        CalendarEvents plain = occurrence(series, 41L, start.plusSeconds(86_400), 2, false);
        CalendarEvents keptException = occurrence(series, 42L, start.plusSeconds(172_800), 3, true);
        CalendarEvents outsideRule = occurrence(series, 43L, start.plusSeconds(259_200), 4, true);

        when(seriesRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(series));
        when(eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(30L))
            .thenReturn(List.of(source, plain, keptException, outsideRule));

        CalendarEventSeriesServiceImpl service = new CalendarEventSeriesServiceImpl(
            seriesRepository,
            eventRepository,
            eventMapper,
            new RecurringEventGenerator(),
            mock(TenantTimeZoneService.class),
            mock(EventReminderProducer.class),
            tenantFeatureService,
            500
        );

        CalendarEventSeriesRequest request = new CalendarEventSeriesRequest();
        request.setSourceOccurrenceId(source.getId());
        request.setTemplate(template(start));
        request.setRecurrence(dailyRule(3));

        var result = service.update(series.getId(), request, authentication());

        assertThat(result.getKeptExceptionCount()).isEqualTo(1);
        assertThat(result.getOutsideRuleExceptionCount()).isEqualTo(1);
        assertThat(result.getSkippedPastCount()).isZero();
        assertThat(keptException.getName()).isEqualTo("Nome precedente");
    }

    @Test
    void countsTheOccurrencesSkippedBecauseTheyArePast() {
        CalendarEventSeriesRepository seriesRepository = mock(CalendarEventSeriesRepository.class);
        CalendarEventsRepository eventRepository = mock(CalendarEventsRepository.class);
        TenantFeatureService tenantFeatureService = mock(TenantFeatureService.class);

        CalendarEventSeries series = new CalendarEventSeries();
        series.setId(31L);
        series.setTimeZone("UTC");
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);

        when(seriesRepository.findByIdAndDeletedFalse(31L)).thenReturn(Optional.of(series));
        when(eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(31L)).thenReturn(List.of());

        CalendarEventSeriesServiceImpl service = new CalendarEventSeriesServiceImpl(
            seriesRepository,
            eventRepository,
            mock(CalendarEventsMapper.class),
            new RecurringEventGenerator(),
            mock(TenantTimeZoneService.class),
            mock(EventReminderProducer.class),
            tenantFeatureService,
            500
        );

        CalendarEventSeriesRequest request = new CalendarEventSeriesRequest();
        request.setTemplate(template(past));
        request.setRecurrence(dailyRule(3));

        var result = service.update(series.getId(), request, authentication());

        // Tre posizioni, tutte nel passato: lo storico non si riscrive.
        assertThat(result.getSkippedPastCount()).isEqualTo(3);
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getCreatedCount()).isZero();
    }

    @Test
    void tellsTheUserWhenTheRuleExceedsTheOccurrenceLimit() {
        CalendarEventSeriesRepository seriesRepository = mock(CalendarEventSeriesRepository.class);
        CalendarEventSeries series = new CalendarEventSeries();
        series.setId(32L);
        series.setTimeZone("UTC");
        when(seriesRepository.findByIdAndDeletedFalse(32L)).thenReturn(Optional.of(series));

        CalendarEventSeriesServiceImpl service = new CalendarEventSeriesServiceImpl(
            seriesRepository,
            mock(CalendarEventsRepository.class),
            mock(CalendarEventsMapper.class),
            new RecurringEventGenerator(),
            mock(TenantTimeZoneService.class),
            mock(EventReminderProducer.class),
            mock(TenantFeatureService.class),
            5
        );

        CalendarEventSeriesRequest request = new CalendarEventSeriesRequest();
        request.setTemplate(template(Instant.now().plusSeconds(86_400)));
        request.setRecurrence(dailyRule(50));

        assertThatThrownBy(() -> service.update(series.getId(), request, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("exceeds the maximum of 5");
    }

    private static CalendarEventsDTO template(Instant start) {
        CalendarEventsDTO template = new CalendarEventsDTO();
        template.setName("Nuovo nome");
        template.setState(StateEnum.COMPLETE);
        template.setStartDate(Date.from(start));
        template.setEndDate(Date.from(start.plusSeconds(7_200)));
        return template;
    }

    private static RecurrenceRuleDTO dailyRule(int count) {
        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(RecurrenceEndType.COUNT);
        end.setCount(count);
        RecurrenceRuleDTO recurrence = new RecurrenceRuleDTO();
        recurrence.setFrequency(RecurrenceFrequency.DAILY);
        recurrence.setInterval(1);
        recurrence.setEnd(end);
        return recurrence;
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
