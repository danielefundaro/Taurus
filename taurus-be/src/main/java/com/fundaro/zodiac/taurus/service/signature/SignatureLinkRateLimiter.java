package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limits the requests to the public signature page per address and per token, in windows of one hour, like the
 * calendar feed.
 */
@Component
public class SignatureLinkRateLimiter {

    private final Cache<String, Window> windows = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofHours(2)).build();
    private final int tokenLimit;
    private final int ipLimit;
    private final Clock clock;

    @Autowired
    public SignatureLinkRateLimiter(ApplicationProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SignatureLinkRateLimiter(ApplicationProperties properties, Clock clock) {
        this.tokenLimit = properties.getSignature().getLinkRateLimitPerTokenHour();
        this.ipLimit = properties.getSignature().getLinkRateLimitPerIpHour();
        this.clock = clock;
    }

    public boolean allowRequest(String remoteAddress) {
        return acquire("ip:" + (remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress), ipLimit);
    }

    public boolean allowToken(String digest) {
        return acquire("token:" + digest, tokenLimit);
    }

    private boolean acquire(String key, int limit) {
        long hour = Instant.now(clock).getEpochSecond() / 3600;
        Window window = windows.asMap().compute(key, (ignored, current) ->
            current == null || current.hour() != hour ? new Window(hour, 1) : new Window(hour, current.count() + 1));
        return window.count() <= limit;
    }

    private record Window(long hour, int count) {
    }
}
