package com.fundaro.zodiac.taurus.service.calendarfeed;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedDetailLevel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.Test;

class IcalendarRendererTest {
    @Test
    void rendersUtf8CrLfEscapingFoldingAndNoSensitiveFields() {
        IcalendarRenderer renderer = new IcalendarRenderer(new ApplicationProperties());
        Instant modified = Instant.parse("2026-09-03T10:15:00Z");
        RenderEvent event = new RenderEvent(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 2, modified,
            Instant.parse("2026-10-10T18:30:00Z"), Instant.parse("2026-10-10T20:30:00Z"),
            "Concerto, d’autunno; 🎺 " + "è".repeat(50), "Teatro\\Sala", "<b>Riga uno</b>\nRiga due", "https://taurus.test/calendar/1", false);
        byte[] bytes = renderer.render(new RenderCalendar("Mio; calendario", CalendarFeedDetailLevel.STANDARD, List.of(event)));
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertThat(text).contains("\r\n").contains("SUMMARY:Concerto\\, d’autunno\\; 🎺");
        assertThat(text).contains("LOCATION:Teatro\\\\Sala", "DESCRIPTION:Riga uno \\nRiga due", "URL:https://taurus.test/calendar/1");
        assertThat(text).doesNotContain("VALARM", "RRULE", "fee", "user");
        for (String line : text.split("\r\n"))
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
    }

    @Test
    void rendersCancellationWithoutStandardDetails() {
        Instant now = Instant.parse("2026-09-04T08:00:00Z");
        RenderEvent event = new RenderEvent(UUID.randomUUID(), 3, now, now.plusSeconds(60), now.plusSeconds(120), "Evento", null, "secret", null, true);
        String text = new String(new IcalendarRenderer(new ApplicationProperties()).render(new RenderCalendar("Feed", CalendarFeedDetailLevel.STANDARD, List.of(event))), StandardCharsets.UTF_8);
        assertThat(text).contains("STATUS:CANCELLED").doesNotContain("DESCRIPTION", "URL:", "TRANSP");
    }

    @Test
    void emitsUtcAcrossTheChangeToDaylightSavingTime() {
        // 02:30 in Rome on the last Sunday of March does not exist: the two events are an hour apart
        // in UTC, whatever the client shows.
        Instant before = ZonedDateTime.of(2027, 3, 28, 1, 30, 0, 0, ZoneId.of("Europe/Rome")).toInstant();
        Instant after = ZonedDateTime.of(2027, 3, 28, 3, 30, 0, 0, ZoneId.of("Europe/Rome")).toInstant();
        RenderEvent first = new RenderEvent(UUID.randomUUID(), 0, before, before, before.plusSeconds(1800), "Prima", null, null, null, false);
        RenderEvent second = new RenderEvent(UUID.randomUUID(), 0, after, after, after.plusSeconds(1800), "Dopo", null, null, null, false);

        String text = render(new RenderCalendar("Feed", CalendarFeedDetailLevel.MINIMAL, List.of(first, second)));

        assertThat(text).contains("DTSTART:20270328T003000Z", "DTSTART:20270328T013000Z");
    }

    @Test
    void keepsTheMinimalLevelToNameTimesAndPlace() {
        Instant now = Instant.parse("2026-11-07T17:00:00Z");
        RenderEvent event = new RenderEvent(UUID.randomUUID(), 1, now, now, now.plusSeconds(3600), "Prova", "Auditorium",
            "Programma della prova", "https://taurus.test/calendar/uid/" + UUID.randomUUID(), false);

        String minimal = render(new RenderCalendar("Feed", CalendarFeedDetailLevel.MINIMAL, List.of(event)));
        String standard = render(new RenderCalendar("Feed", CalendarFeedDetailLevel.STANDARD, List.of(event)));

        assertThat(minimal).contains("SUMMARY:Prova", "LOCATION:Auditorium").doesNotContain("DESCRIPTION", "URL:");
        assertThat(standard).contains("DESCRIPTION:Programma della prova", "URL:https://taurus.test/calendar/uid/");
    }

    @Test
    void cutsADescriptionLongerThanFourThousandCharacters() {
        Instant now = Instant.parse("2026-11-07T17:00:00Z");
        RenderEvent event = new RenderEvent(UUID.randomUUID(), 1, now, now, now.plusSeconds(3600), "Prova", null,
            "a".repeat(5000), null, false);

        String text = render(new RenderCalendar("Feed", CalendarFeedDetailLevel.STANDARD, List.of(event)));

        String unfolded = text.replace("\r\n ", "");
        assertThat(unfolded).contains("DESCRIPTION:" + "a".repeat(4000));
        assertThat(unfolded).doesNotContain("a".repeat(4001));
    }

    private static String render(RenderCalendar calendar) {
        return new String(new IcalendarRenderer(new ApplicationProperties()).render(calendar), StandardCharsets.UTF_8);
    }
}
