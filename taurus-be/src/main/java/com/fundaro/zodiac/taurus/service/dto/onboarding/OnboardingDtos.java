package com.fundaro.zodiac.taurus.service.dto.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public final class OnboardingDtos {
    private OnboardingDtos() {}

    public record Context(
        String tenantCode, String tenantName, boolean schemaActive, Long maxUsers, long users,
        long instruments, long inventoryItems, long financialAccounts, List<Integer> supportedTemplateVersions,
        Job lastImport, Limits limits
    ) {}
    public record Limits(long maxFileSizeBytes, int maxTotalRows, int maxUserRows) {}
    public record Counts(int total, int valid, int warnings, int errors) {}
    public record Job(
        Long id, String fileName, OnboardingImportFormat format, OnboardingSection csvSection, int templateVersion,
        OnboardingJobStatus status, String stage, int progressPercentage, Counts counts,
        ZonedDateTime createdAt, ZonedDateTime completedAt, int setupEmailFailures, String lastErrorCode
    ) {}
    public record Section(
        OnboardingSection section, int total, int valid, int warnings, int errors,
        int create, int reuse, int skip, int applied
    ) {}
    public record Row(Long id, OnboardingSection section, int rowNumber, OnboardingRowStatus status, OnboardingRowAction action, Map<String, Object> values) {}
    public record Issue(Long id, OnboardingIssueSeverity severity, String code, OnboardingSection section, Integer rowNumber, String columnName, String message, String suggestion) {}
    public record ApplyRequest(@NotNull Boolean warningsAccepted, @NotNull Boolean sendSetupEmails) {}
}
