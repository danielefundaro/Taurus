package com.fundaro.zodiac.taurus.service.dto.dashboard;

import java.time.ZonedDateTime;
import java.util.List;

public record OperationalDashboardDTO(
    ZonedDateTime generatedAt,
    DashboardResultStatus status,
    OperationalSummaryDTO summary,
    List<OperationalItemDTO> items,
    List<DashboardDomain> unavailableDomains
) {}
