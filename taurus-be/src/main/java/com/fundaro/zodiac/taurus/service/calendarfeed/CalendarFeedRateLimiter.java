package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class CalendarFeedRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    public CalendarFeedRateLimiter(ApplicationProperties properties) { limit = properties.getCalendarFeed().getRateLimitPerTokenHour(); }
    public boolean allow(byte[] digest) {
        String key = java.util.HexFormat.of().formatHex(digest); long hour = Instant.now().getEpochSecond() / 3600;
        return windows.compute(key, (ignored, old) -> old == null || old.hour != hour ? new Window(hour, 1) : new Window(hour, old.count + 1)).count <= limit;
    }
    private record Window(long hour, int count) {}
}
