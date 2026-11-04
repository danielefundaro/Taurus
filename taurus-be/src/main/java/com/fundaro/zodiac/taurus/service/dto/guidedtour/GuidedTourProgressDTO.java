package com.fundaro.zodiac.taurus.service.dto.guidedtour;

import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourStatus;

import java.time.ZonedDateTime;
import java.util.List;

public record GuidedTourProgressDTO(
    GuidedTourStatus status,
    List<String> seenSteps,
    String lastStepId,
    int autoProposals,
    int catalogRevision,
    ZonedDateTime startedAt,
    ZonedDateTime completedAt,
    long version
) {
}
