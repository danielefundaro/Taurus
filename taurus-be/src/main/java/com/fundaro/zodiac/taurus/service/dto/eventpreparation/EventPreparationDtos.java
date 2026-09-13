package com.fundaro.zodiac.taurus.service.dto.eventpreparation;

import com.fundaro.zodiac.taurus.domain.eventpreparation.PreparationProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;
import java.util.List;

public final class EventPreparationDtos {
    private EventPreparationDtos() {
    }

    public enum Phase {PREPARATION, IN_PROGRESS, FOLLOW_UP, UNKNOWN}

    public enum PreparationStatus {NOT_CONFIGURED, BLOCKED, ATTENTION, READY, UNKNOWN}

    public enum ClosureStatus {TO_CLOSE, CLOSED_WITH_WARNINGS, CLOSED, NOT_REQUIRED, UNKNOWN}

    public enum Severity {BLOCKER, WARNING}

    public record Configuration(
        @NotNull PreparationProfile profile,
        boolean locationRequired,
        boolean programRequired,
        boolean scoresRequired,
        boolean availabilityRequired,
        @Min(1) Integer minimumAvailableParticipants,
        @Min(0) @Max(43200) int availabilityDeadlineMinutes,
        boolean materialsRequired,
        @Min(0) @Max(43200) int materialsDeadlineMinutes,
        boolean budgetRequired,
        boolean presenceClosureRequired,
        boolean financialClosureRequired,
        long version
    ) {
    }

    public record ProgramEntryRequest(@NotNull Long trackId, @Min(1) @Max(86400) Integer plannedDurationSeconds,
                                      @Size(max = 2000) String notes) {
    }

    public record ProgramEntry(Long id, Long trackId, String trackName, String trackState, int order,
                               Integer plannedDurationSeconds, String notes) {
    }

    public record MaterialRequest(@NotNull Long itemId, Long assignmentId, @Min(1) int requiredQuantity,
                                  @Size(max = 2000) String notes) {
    }

    public record Material(Long id, Long itemId, String itemName, Long assignmentId, String assignee,
                           int requiredQuantity, String condition, boolean confirmed, String notes) {
    }

    public record Availability(int expected, int available, int unavailable, int missing, Integer minimumRequired,
                               ZonedDateTime deadline) {
    }

    public record Issue(String code, String area, Severity severity, String message, String action) {
    }

    public record Evaluation(
        ZonedDateTime evaluatedAt,
        Phase phase,
        PreparationStatus preparationStatus,
        ClosureStatus closureStatus,
        int completionPercent,
        int passedChecks,
        int applicableChecks,
        int blockerCount,
        int warningCount,
        List<Issue> issues
    ) {
    }

    public record View(Configuration configuration, Evaluation evaluation, List<ProgramEntry> program,
                       Availability availability, List<Material> materials) {
    }

    public record ProgramRequest(@NotNull List<@Valid ProgramEntryRequest> entries) {
    }

    public record MaterialsRequest(@NotNull List<@Valid MaterialRequest> materials) {
    }
}
