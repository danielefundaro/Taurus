package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.repository.calendarfeed.CalendarEventFeedTombstoneRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CalendarFeedEventLifecycle {
    private final CalendarEventFeedTombstoneRepository tombstones;
    private final int retentionDays;
    public CalendarFeedEventLifecycle(CalendarEventFeedTombstoneRepository tombstones, ApplicationProperties properties) {
        this.tombstones = tombstones; retentionDays = properties.getCalendarFeed().getTombstoneRetentionDays();
    }
    public Snapshot snapshot(CalendarEvents e) { return new Snapshot(e.getCalendarUid(), e.getCalendarSequence(), e.getName(), e.getDescription(), e.getLocation(), e.getStartDate(), e.getEndDate(), e.getState(), Boolean.TRUE.equals(e.getDeleted()), Boolean.TRUE.equals(e.getSeriesExcluded())); }
    public void apply(CalendarEvents event, Snapshot before) {
        Instant now = Instant.now();
        if (event.getCalendarUid() == null) event.setCalendarUid(UUID.randomUUID());
        if (event.getCalendarSequence() == null) event.setCalendarSequence(0);
        if (before == null) { event.setCalendarFeedModifiedAt(now); clearVisible(event); return; }
        if (!projectionEquals(event, before)) { event.setCalendarSequence(Math.max(event.getCalendarSequence(), before.sequence == null ? 0 : before.sequence) + 1); event.setCalendarFeedModifiedAt(now); }
        updateAudience(event, before, CalendarFeedAudience.INTERNAL, visible(before, CalendarFeedAudience.INTERNAL), visible(event, CalendarFeedAudience.INTERNAL), now);
        updateAudience(event, before, CalendarFeedAudience.EXTERNAL, visible(before, CalendarFeedAudience.EXTERNAL), visible(event, CalendarFeedAudience.EXTERNAL), now);
    }
    private void updateAudience(CalendarEvents event, Snapshot old, CalendarFeedAudience audience, boolean wasVisible, boolean visible, Instant now) {
        if (wasVisible && !visible && old.uid != null && old.start != null && old.end != null) {
            CalendarEventFeedTombstone t = tombstones.findByEventUidAndAudience(old.uid, audience).orElseGet(CalendarEventFeedTombstone::new);
            t.setEventUid(old.uid); t.setAudience(audience); t.setSequence(event.getCalendarSequence()); t.setOriginalStartDate(old.start.toInstant()); t.setOriginalEndDate(old.end.toInstant());
            t.setSummarySnapshot(sanitize(old.name)); t.setCancelledAt(now); t.setExpiresAt(now.plus(retentionDays, java.time.temporal.ChronoUnit.DAYS)); tombstones.save(t);
        } else if (visible) tombstones.deleteByEventUidAndAudience(event.getCalendarUid(), audience);
    }
    private void clearVisible(CalendarEvents event) { if (visible(event, CalendarFeedAudience.INTERNAL)) tombstones.deleteByEventUidAndAudience(event.getCalendarUid(), CalendarFeedAudience.INTERNAL); if (visible(event, CalendarFeedAudience.EXTERNAL)) tombstones.deleteByEventUidAndAudience(event.getCalendarUid(), CalendarFeedAudience.EXTERNAL); }
    private static boolean visible(CalendarEvents e, CalendarFeedAudience a) { return !Boolean.TRUE.equals(e.getDeleted()) && !Boolean.TRUE.equals(e.getSeriesExcluded()) && (a == CalendarFeedAudience.EXTERNAL ? e.getState() == StateEnum.PUBLIC : e.getState() == StateEnum.PUBLIC || e.getState() == StateEnum.COMPLETE); }
    private static boolean visible(Snapshot e, CalendarFeedAudience a) { return !e.deleted && !e.excluded && (a == CalendarFeedAudience.EXTERNAL ? e.state == StateEnum.PUBLIC : e.state == StateEnum.PUBLIC || e.state == StateEnum.COMPLETE); }
    private static boolean projectionEquals(CalendarEvents e, Snapshot b) { return Objects.equals(e.getName(), b.name) && Objects.equals(e.getDescription(), b.description) && Objects.equals(e.getLocation(), b.location) && Objects.equals(e.getStartDate(), b.start) && Objects.equals(e.getEndDate(), b.end) && Objects.equals(e.getState(), b.state) && Boolean.TRUE.equals(e.getDeleted()) == b.deleted && Boolean.TRUE.equals(e.getSeriesExcluded()) == b.excluded; }
    private static String sanitize(String value) { if (value == null || value.isBlank()) return "Evento"; String clean = value.replaceAll("[\\p{Cc}]", " ").trim(); return clean.substring(0, Math.min(255, clean.length())); }
    public record Snapshot(UUID uid, Integer sequence, String name, String description, String location, Date start, Date end, StateEnum state, boolean deleted, boolean excluded) {}
}
