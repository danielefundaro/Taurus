package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CalendarFeedRateLimiter {
    private final Cache<String, Window> windows = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofHours(2)).build();
    private final int tokenLimit;
    private final int ipLimit;
    private final int globalLimit;
    private final Clock clock;

    @Autowired
    public CalendarFeedRateLimiter(ApplicationProperties properties) {
        this(properties, Clock.systemUTC());
    }

    CalendarFeedRateLimiter(ApplicationProperties properties, Clock clock) {
        tokenLimit = properties.getCalendarFeed().getRateLimitPerTokenHour();
        ipLimit = properties.getCalendarFeed().getRateLimitPerIpHour();
        globalLimit = properties.getCalendarFeed().getRateLimitGlobalHour();
        this.clock = clock;
    }

    public boolean allowRequest(String remoteAddress) {
        long hour = Instant.now(clock).getEpochSecond() / 3600;
        boolean ipAllowed = acquire("ip:" + safe(remoteAddress), hour, ipLimit);
        boolean globalAllowed = acquire("global", hour, globalLimit);
        return ipAllowed && globalAllowed;
    }

    public boolean allowToken(byte[] digest) {
        long hour = Instant.now(clock).getEpochSecond() / 3600;
        return acquire("token:" + java.util.HexFormat.of().formatHex(digest), hour, tokenLimit);
    }

    private boolean acquire(String key, long hour, int limit) {
        Window window = windows.asMap().compute(key, (ignored, current) ->
            current == null || current.hour != hour ? new Window(hour, 1) : new Window(hour, current.count + 1));
        return window.count <= limit;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private record Window(long hour, int count) {
    }
}
