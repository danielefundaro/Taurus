package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardResultStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class DashboardMetrics {

    private static final String PREFIX = "taurus.dashboard.";
    private final MeterRegistry registry;

    public DashboardMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(DashboardResultStatus result, int groups, long durationNanos) {
        String resultTag = result.name().toLowerCase(java.util.Locale.ROOT);
        registry.counter(PREFIX + "operations.requests", "result", resultTag).increment();
        Timer.builder(PREFIX + "operations.duration")
            .tag("result", resultTag)
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
        registry.summary(PREFIX + "operations.groups", "result", resultTag).record(groups);
    }

    public void recordProviderFailure(DashboardDomain domain) {
        registry.counter(PREFIX + "provider.failures", "domain", domain.name().toLowerCase(java.util.Locale.ROOT)).increment();
    }

    public void recordFailure(long durationNanos) {
        registry.counter(PREFIX + "operations.requests", "result", "failed").increment();
        Timer.builder(PREFIX + "operations.duration")
            .tag("result", "failed")
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
