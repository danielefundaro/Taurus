package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.repository.calendarfeed.CalendarEventFeedTombstoneRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What the feed shows of an event: a UID that never changes, a sequence that grows only when the
 * published projection changes, and a tombstone whenever a public stops being allowed to see it.
 */
class CalendarFeedEventLifecycleTest {

    @Test
    void keepsTheUidAndRaisesTheSequenceOnlyWhenThePublishedProjectionChanges() {
        CalendarEventFeedTombstoneRepository tombstones = mock(CalendarEventFeedTombstoneRepository.class);
        CalendarFeedEventLifecycle lifecycle = lifecycle(tombstones);
        CalendarEvents event = event("Concerto", StateEnum.PUBLIC);
        UUID uid = event.getCalendarUid();

        // A change nobody sees in the ICS file: the fee is not part of the projection.
        CalendarFeedEventLifecycle.Snapshot before = lifecycle.snapshot(event);
        event.setFee(new java.math.BigDecimal("250.00"));
        lifecycle.apply(event, before);

        assertThat(event.getCalendarUid()).isEqualTo(uid);
        assertThat(event.getCalendarSequence()).isZero();

        // The name travels in the file: the subscribed clients must see a newer version.
        before = lifecycle.snapshot(event);
        event.setName("Concerto di Natale");
        lifecycle.apply(event, before);

        assertThat(event.getCalendarUid()).isEqualTo(uid);
        assertThat(event.getCalendarSequence()).isEqualTo(1);
        assertThat(event.getCalendarFeedModifiedAt()).isNotNull();
    }

    @Test
    void writesOneTombstonePerPublicThatLosesTheEvent() {
        CalendarEventFeedTombstoneRepository tombstones = mock(CalendarEventFeedTombstoneRepository.class);
        when(tombstones.findByEventUidAndAudience(any(), any())).thenReturn(Optional.empty());
        CalendarFeedEventLifecycle lifecycle = lifecycle(tombstones);
        CalendarEvents event = event("Prova aperta", StateEnum.PUBLIC);

        // PUBLIC -> COMPLETE: the external subscribers lose it, the internal ones keep it.
        CalendarFeedEventLifecycle.Snapshot before = lifecycle.snapshot(event);
        event.setState(StateEnum.COMPLETE);
        lifecycle.apply(event, before);

        ArgumentCaptor<CalendarEventFeedTombstone> saved = ArgumentCaptor.forClass(CalendarEventFeedTombstone.class);
        verify(tombstones).save(saved.capture());
        assertThat(saved.getValue().getAudience()).isEqualTo(CalendarFeedAudience.EXTERNAL);
        assertThat(saved.getValue().getEventUid()).isEqualTo(event.getCalendarUid());
        assertThat(saved.getValue().getSummarySnapshot()).isEqualTo("Prova aperta");
        assertThat(saved.getValue().getSequence()).isEqualTo(event.getCalendarSequence());
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plus(89, ChronoUnit.DAYS));
        verify(tombstones).deleteByEventUidAndAudience(event.getCalendarUid(), CalendarFeedAudience.INTERNAL);
    }

    @Test
    void writesBothTombstonesWhenTheEventIsCancelled() {
        CalendarEventFeedTombstoneRepository tombstones = mock(CalendarEventFeedTombstoneRepository.class);
        when(tombstones.findByEventUidAndAudience(any(), any())).thenReturn(Optional.empty());
        CalendarFeedEventLifecycle lifecycle = lifecycle(tombstones);
        CalendarEvents event = event("Saggio", StateEnum.PUBLIC);

        CalendarFeedEventLifecycle.Snapshot before = lifecycle.snapshot(event);
        event.setDeleted(true);
        lifecycle.apply(event, before);

        ArgumentCaptor<CalendarEventFeedTombstone> saved = ArgumentCaptor.forClass(CalendarEventFeedTombstone.class);
        verify(tombstones, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CalendarEventFeedTombstone::getAudience)
            .containsExactlyInAnyOrder(CalendarFeedAudience.INTERNAL, CalendarFeedAudience.EXTERNAL);
        verify(tombstones, never()).deleteByEventUidAndAudience(any(), any());
    }

    @Test
    void dropsTheTombstoneWhenTheEventBecomesVisibleAgain() {
        CalendarEventFeedTombstoneRepository tombstones = mock(CalendarEventFeedTombstoneRepository.class);
        when(tombstones.findByEventUidAndAudience(any(), any())).thenReturn(Optional.empty());
        CalendarFeedEventLifecycle lifecycle = lifecycle(tombstones);
        CalendarEvents event = event("Concerto", StateEnum.DRAFT);

        CalendarFeedEventLifecycle.Snapshot before = lifecycle.snapshot(event);
        event.setState(StateEnum.PUBLIC);
        lifecycle.apply(event, before);

        verify(tombstones).deleteByEventUidAndAudience(event.getCalendarUid(), CalendarFeedAudience.INTERNAL);
        verify(tombstones).deleteByEventUidAndAudience(event.getCalendarUid(), CalendarFeedAudience.EXTERNAL);
        verify(tombstones, never()).save(any());
        // The restored event is published with a sequence above the one the clients already hold.
        assertThat(event.getCalendarSequence()).isEqualTo(1);
    }

    private static CalendarFeedEventLifecycle lifecycle(CalendarEventFeedTombstoneRepository tombstones) {
        return new CalendarFeedEventLifecycle(tombstones, new CalendarFeedMetrics(new SimpleMeterRegistry()), new ApplicationProperties());
    }

    private static CalendarEvents event(String name, StateEnum state) {
        CalendarEvents event = new CalendarEvents();
        event.setId(7L);
        event.setName(name);
        event.setState(state);
        event.setDeleted(false);
        event.setSeriesExcluded(false);
        event.setCalendarUid(UUID.randomUUID());
        event.setCalendarSequence(0);
        Instant start = Instant.parse("2026-12-24T20:00:00Z");
        event.setStartDate(Date.from(start));
        event.setEndDate(Date.from(start.plus(2, ChronoUnit.HOURS)));
        return event;
    }
}
