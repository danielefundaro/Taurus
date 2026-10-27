package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.UxTelemetryMetrics;
import com.fundaro.zodiac.taurus.service.dto.UxTelemetryDTO;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collects the usage aggregate of a frontend feature.
 *
 * <p>It always answers {@code 204}, also for a report it drops: a measure is worth less than the
 * work of whoever is using the application, so an unknown feature or a malformed event name must
 * not produce an error the user would see. What is kept, and what is capped, is decided by
 * {@link UxTelemetryMetrics}. No tenant schema is touched: the report becomes a counter in memory.
 */
@RestController
@RequestMapping("/api/ux-telemetry")
public class UxTelemetryResource {

    private final UxTelemetryMetrics metrics;

    public UxTelemetryResource(UxTelemetryMetrics metrics) {
        this.metrics = metrics;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @TenantSchemaNotRequired
    public void report(@RequestBody UxTelemetryDTO report) {
        metrics.record(report);
    }
}
