package com.fundaro.zodiac.taurus.service.calendarfeed;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedDetailLevel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class IcalendarRendererTest {
    @Test void rendersUtf8CrLfEscapingFoldingAndNoSensitiveFields() {
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
        for (String line : text.split("\r\n")) assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
    }

    @Test void rendersCancellationWithoutStandardDetails() {
        Instant now = Instant.parse("2026-09-04T08:00:00Z");
        RenderEvent event = new RenderEvent(UUID.randomUUID(), 3, now, now.plusSeconds(60), now.plusSeconds(120), "Evento", null, "secret", null, true);
        String text = new String(new IcalendarRenderer(new ApplicationProperties()).render(new RenderCalendar("Feed", CalendarFeedDetailLevel.STANDARD, List.of(event))), StandardCharsets.UTF_8);
        assertThat(text).contains("STATUS:CANCELLED").doesNotContain("DESCRIPTION", "URL:", "TRANSP");
    }
}
