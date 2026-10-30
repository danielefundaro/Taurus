package com.fundaro.zodiac.taurus.service.dto.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class CalendarFeedDtos {
    private CalendarFeedDtos() {
    }

    public record CreateRequest(
        @NotBlank @Size(max = 120) String name,
        CalendarFeedScope visibilityScope,
        CalendarFeedDetailLevel detailLevel,
        @Min(0) @Max(365) Integer pastDays,
        @Min(1) @Max(36) Integer futureMonths,
        @NotNull UUID idempotencyKey
    ) {
    }

    public record RotateRequest(@NotNull UUID idempotencyKey) {
    }

    public record Feed(
        UUID id, String name, CalendarFeedType feedType, CalendarFeedScope visibilityScope,
        CalendarFeedDetailLevel detailLevel, int pastDays, int futureMonths,
        CalendarFeedStatus status, String tokenFingerprint, Long ownerUserId, String ownerName,
        String createdBy, Instant createdAt, Instant lastAccessedAt
    ) {
    }

    /** One step of a credential's history: never the token, only the fingerprint that names it. */
    public record AuditEntry(
        String action, String source, int tokenVersion, String tokenFingerprint,
        String actor, String reason, Instant occurredAt
    ) {
    }

    public record SecretFeed(
        UUID id, String name, CalendarFeedType feedType, CalendarFeedScope visibilityScope,
        CalendarFeedDetailLevel detailLevel, int pastDays, int futureMonths,
        String subscriptionUrl, boolean tokenShownOnce, Instant createdAt
    ) {
    }

    public record RenderEvent(UUID uid, int sequence, Instant modifiedAt, Instant startAt, Instant endAt,
                              String summary, String location, String description, String url, boolean cancelled) {
    }

    public record RenderCalendar(String name, CalendarFeedDetailLevel detailLevel, java.util.List<RenderEvent> events) {
    }

    /**
     * A generated representation. {@code feedType} and {@code scope} only label the metrics;
     * {@code touchId} is the subscription whose last access is still to be written, outside the
     * transaction that produced the body.
     */
    public record Download(byte[] body, String etag, Instant lastModified, String feedType, String scope, UUID touchId) {
    }
}
