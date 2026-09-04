package com.fundaro.zodiac.taurus.service.dto.dashboard;

import java.time.ZonedDateTime;

public record OperationalItemDTO(
    String key,
    DashboardOperationType type,
    DashboardDomain domain,
    DashboardSeverity severity,
    long count,
    Long relatedCount,
    String title,
    String description,
    ZonedDateTime dueAt,
    String actionLabel,
    String targetPath
) {}
