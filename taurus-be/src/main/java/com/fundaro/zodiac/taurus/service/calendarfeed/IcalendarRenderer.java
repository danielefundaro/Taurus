package com.fundaro.zodiac.taurus.service.calendarfeed;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedDetailLevel;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import net.fortuna.ical4j.data.CalendarBuilder;

@Component
public class IcalendarRenderer {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");
    private final String refresh;
    public IcalendarRenderer(ApplicationProperties properties) { refresh = properties.getCalendarFeed().getSuggestedRefresh(); }

    public byte[] render(RenderCalendar calendar) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        line(out, "BEGIN:VCALENDAR"); line(out, "VERSION:2.0");
        line(out, "PRODID:-//Zodiac Taurus//Calendar Feed 1.0//IT"); line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH"); line(out, "X-WR-CALNAME:" + text(calendar.name()));
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:" + refresh);
        for (RenderEvent event : calendar.events()) event(out, event, calendar.detailLevel());
        line(out, "END:VCALENDAR");
        byte[] result = out.toByteArray();
        try { new CalendarBuilder().build(new ByteArrayInputStream(result)); }
        catch (Exception exception) { throw new IllegalStateException("Invalid iCalendar projection", exception); }
        return result;
    }

    private void event(ByteArrayOutputStream out, RenderEvent e, CalendarFeedDetailLevel detail) {
        if (e.startAt() == null || e.endAt() == null || !e.endAt().isAfter(e.startAt())) throw new IllegalStateException("Invalid calendar event interval");
        line(out, "BEGIN:VEVENT"); line(out, "UID:urn:uuid:" + e.uid()); line(out, "DTSTAMP:" + UTC.format(e.modifiedAt()));
        line(out, "LAST-MODIFIED:" + UTC.format(e.modifiedAt())); line(out, "SEQUENCE:" + e.sequence());
        line(out, "DTSTART:" + UTC.format(e.startAt())); line(out, "DTEND:" + UTC.format(e.endAt()));
        line(out, "SUMMARY:" + text(e.summary()));
        if (!e.cancelled() && e.location() != null && !e.location().isBlank()) line(out, "LOCATION:" + text(e.location()));
        if (e.cancelled()) line(out, "STATUS:CANCELLED");
        else { line(out, "STATUS:CONFIRMED"); line(out, "TRANSP:OPAQUE"); }
        if (!e.cancelled() && detail == CalendarFeedDetailLevel.STANDARD) {
            String description = plain(e.description()); if (!description.isBlank()) line(out, "DESCRIPTION:" + text(description));
            if (e.url() != null) line(out, "URL:" + safeUri(e.url()));
        }
        line(out, "END:VEVENT");
    }

    static String plain(String value) {
        if (value == null) return "";
        String result = HtmlUtils.htmlUnescape(TAG.matcher(value).replaceAll(" "));
        result = CONTROL.matcher(result).replaceAll("").replaceAll("[ \\t]+", " ").trim();
        return result.length() <= 4000 ? result : result.substring(0, 4000);
    }
    static String text(String value) {
        String clean = CONTROL.matcher(value == null ? "" : value).replaceAll("");
        return clean.replace("\\", "\\\\").replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }
    private static String safeUri(String value) { return CONTROL.matcher(value).replaceAll("").replace(" ", "%20"); }
    private static void line(ByteArrayOutputStream out, String value) {
        int offset = 0, bytesOnLine = 0;
        while (offset < value.length()) {
            int cp = value.codePointAt(offset); String part = new String(Character.toChars(cp)); int size = part.getBytes(StandardCharsets.UTF_8).length;
            int limit = bytesOnLine == 0 && out.size() >= 0 ? 75 : 75;
            if (bytesOnLine + size > limit) { out.writeBytes("\r\n ".getBytes(StandardCharsets.US_ASCII)); bytesOnLine = 1; }
            out.writeBytes(part.getBytes(StandardCharsets.UTF_8)); bytesOnLine += size; offset += Character.charCount(cp);
        }
        out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
}
