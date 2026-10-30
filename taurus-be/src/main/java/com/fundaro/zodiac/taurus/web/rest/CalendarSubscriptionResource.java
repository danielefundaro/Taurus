package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedMetrics;
import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedRateLimitException;
import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedTokenResolver;
import com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.Download;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar-subscriptions/v1")
public class CalendarSubscriptionResource {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarSubscriptionResource.class);

    private final CalendarFeedTokenResolver resolver;
    private final CalendarFeedMetrics metrics;

    public CalendarSubscriptionResource(CalendarFeedTokenResolver resolver, CalendarFeedMetrics metrics) {
        this.resolver = resolver;
        this.metrics = metrics;
    }

    @RequestMapping(value = "/{token}/calendar.ics", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<byte[]> download(@PathVariable String token,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                           @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSince,
                                           jakarta.servlet.http.HttpServletRequest request) {
        // The correlation id is generated here and never derived from the token: it ties the log
        // lines of one download together without naming the credential that produced them.
        String correlationId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        try {
            Download result = resolver.resolve(token, request.getRemoteAddr()).orElse(null);
            if (result == null) return observed(correlationId, startedAt, HttpStatus.NOT_FOUND, null, null,
                ResponseEntity.notFound().build());
            HttpHeaders headers = headers(result);
            if (result.etag().equals(ifNoneMatch) || ifNoneMatch == null && notModifiedSince(ifModifiedSince, result.lastModified()))
                return observed(correlationId, startedAt, HttpStatus.NOT_MODIFIED, result.feedType(), result.scope(),
                    new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED));
            byte[] body = "HEAD".equals(request.getMethod()) ? null : result.body();
            return observed(correlationId, startedAt, HttpStatus.OK, result.feedType(), result.scope(),
                new ResponseEntity<>(body, headers, HttpStatus.OK));
        } catch (CalendarFeedRateLimitException e) {
            return observed(correlationId, startedAt, HttpStatus.TOO_MANY_REQUESTS, null, null,
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600").build());
        } catch (RuntimeException e) {
            LOG.warn("calendar_feed_download correlationId={} outcome=503 errorClass={}", correlationId, e.getClass().getSimpleName());
            metrics.request("503", null, null, millis(startedAt));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /** One log line and one metric per download: the outcome, never the token nor the calendar. */
    private ResponseEntity<byte[]> observed(String correlationId, long startedAt, HttpStatus status,
                                            String feedType, String scope, ResponseEntity<byte[]> response) {
        long duration = millis(startedAt);
        metrics.request(Integer.toString(status.value()), feedType, scope, duration);
        LOG.info("calendar_feed_download correlationId={} outcome={} type={} scope={} durationMs={}",
            correlationId, status.value(), feedType == null ? "-" : feedType, scope == null ? "-" : scope, duration);
        return response;
    }

    private static long millis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static HttpHeaders headers(Download d) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("text/calendar; charset=utf-8"));
        h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"calendar.ics\"");
        h.setCacheControl("private, max-age=300, must-revalidate");
        h.setETag(d.etag());
        h.setLastModified(d.lastModified().toEpochMilli());
        h.set("X-Content-Type-Options", "nosniff");
        return h;
    }

    private static boolean notModifiedSince(String header, Instant lastModified) {
        if (header == null) return false;
        try {
            return ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().compareTo(lastModified) >= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
