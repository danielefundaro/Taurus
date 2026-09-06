package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class InventoryScanRateLimiter {
    private final int limit;
    private final Cache<String, Window> windows = Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(Duration.ofMinutes(2)).build();

    public InventoryScanRateLimiter(ApplicationProperties properties) {
        this.limit = properties.getInventory().getQr().getResolutionLimitPerMinute();
    }

    public boolean tryAcquire(String userId, String remoteAddress) {
        long minute = Instant.now().getEpochSecond() / 60;
        return acquire("user:" + safe(userId), minute) && acquire("ip:" + safe(remoteAddress), minute);
    }

    private boolean acquire(String key, long minute) {
        Window window = windows.asMap().compute(key, (ignored, current) -> {
            if (current == null || current.minute != minute) return new Window(minute, 1);
            return new Window(minute, current.count + 1);
        });
        return window.count <= limit;
    }

    private static String safe(String value) { return value == null || value.isBlank() ? "unknown" : value; }
    private record Window(long minute, int count) {}
}
