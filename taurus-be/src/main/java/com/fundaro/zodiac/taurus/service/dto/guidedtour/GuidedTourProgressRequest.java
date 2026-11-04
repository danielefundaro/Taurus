package com.fundaro.zodiac.taurus.service.dto.guidedtour;

import java.util.List;

/**
 * The progress of the role of the token. Person, role and tenant never come from the body. The service validates the
 * fields, so that an unknown status answers {@code validation} as a malformed step identifier does.
 *
 * <p>{@code version} is the one read with the progress, {@code null} when the role has no progress yet.
 */
public record GuidedTourProgressRequest(
    String status,
    List<String> seenSteps,
    String lastStepId,
    Integer autoProposals,
    Integer catalogRevision,
    Long version
) {
}
