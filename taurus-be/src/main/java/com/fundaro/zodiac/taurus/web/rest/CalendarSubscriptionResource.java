package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.calendarfeed.*;
import com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.Download;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calendar-subscriptions/v1")
public class CalendarSubscriptionResource {
    private final CalendarFeedTokenResolver resolver;
    public CalendarSubscriptionResource(CalendarFeedTokenResolver resolver) { this.resolver = resolver; }
    @RequestMapping(value = "/{token}/calendar.ics", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<byte[]> download(@PathVariable String token,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
        @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSince,
        jakarta.servlet.http.HttpServletRequest request) {
        try {
            Download result = resolver.resolve(token).orElse(null); if (result == null) return ResponseEntity.notFound().build();
            HttpHeaders headers = headers(result);
            if (result.etag().equals(ifNoneMatch) || ifNoneMatch == null && notModifiedSince(ifModifiedSince, result.lastModified())) return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);
            byte[] body = "HEAD".equals(request.getMethod()) ? null : result.body(); return new ResponseEntity<>(body, headers, HttpStatus.OK);
        } catch (CalendarFeedRateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600").build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
    private static HttpHeaders headers(Download d) {
        HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.parseMediaType("text/calendar; charset=utf-8"));
        h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"calendar.ics\""); h.setCacheControl("private, max-age=300, must-revalidate");
        h.setETag(d.etag()); h.setLastModified(d.lastModified().toEpochMilli()); h.set("X-Content-Type-Options", "nosniff"); return h;
    }
    private static boolean notModifiedSince(String header, Instant lastModified) {
        if (header == null) return false;
        try { return ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().compareTo(lastModified) >= 0; } catch (RuntimeException e) { return false; }
    }
}
