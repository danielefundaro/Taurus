package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import java.time.*;
import org.junit.jupiter.api.Test;

class CalendarFeedRateLimiterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-06T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void limitsEachTokenDigest() {
        CalendarFeedRateLimiter limiter = limiter(2, 10, 100);
        assertThat(limiter.allowToken(digest(1))).isTrue();
        assertThat(limiter.allowToken(digest(1))).isTrue();
        assertThat(limiter.allowToken(digest(1))).isFalse();
    }

    @Test
    void appliesTheHigherIpAntiAbuseThresholdAcrossTokens() {
        CalendarFeedRateLimiter limiter = limiter(10, 2, 100);
        assertThat(limiter.allowRequest("192.0.2.1")).isTrue();
        assertThat(limiter.allowRequest("192.0.2.1")).isTrue();
        assertThat(limiter.allowRequest("192.0.2.1")).isFalse();
    }

    @Test
    void appliesTheGlobalAntiAbuseThreshold() {
        CalendarFeedRateLimiter limiter = limiter(10, 10, 2);
        assertThat(limiter.allowRequest("192.0.2.1")).isTrue();
        assertThat(limiter.allowRequest("192.0.2.2")).isTrue();
        assertThat(limiter.allowRequest("192.0.2.3")).isFalse();
    }

    private static CalendarFeedRateLimiter limiter(int token, int ip, int global) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getCalendarFeed().setRateLimitPerTokenHour(token);
        properties.getCalendarFeed().setRateLimitPerIpHour(ip);
        properties.getCalendarFeed().setRateLimitGlobalHour(global);
        return new CalendarFeedRateLimiter(properties, CLOCK);
    }

    private static byte[] digest(int marker) {
        byte[] value = new byte[32];
        value[0] = (byte) marker;
        return value;
    }
}
