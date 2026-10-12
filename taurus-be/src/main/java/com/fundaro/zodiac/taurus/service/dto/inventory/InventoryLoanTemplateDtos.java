package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplateOrigin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public final class InventoryLoanTemplateDtos {

    private InventoryLoanTemplateDtos() {
    }

    /**
     * Duration rules of the loan (art. 4 of the default template), in months.
     */
    public record Durations(
        @Min(1) @Max(120) int minMonths,
        @Min(1) @Max(120) int maxMonths,
        @Min(1) @Max(120) int renewalMaxMonths,
        @Min(1) @Max(120) int totalMaxMonths
    ) {
    }

    public record DurationsUpdate(@NotNull @Valid Durations durations, long entityVersion) {
    }

    /**
     * Template file read by the browser; the size limit is checked by the service, which answers 413 above it.
     */
    public record TemplateFile(@NotBlank @Size(max = 255) String fileName, @NotNull String content) {
    }

    public record TemplateDownload(String fileName, String content) {
    }

    /**
     * An issue of a template: {@code code} completes the key {@code inventory.loanTemplate.issue.<code>}.
     */
    public record Issue(String code, Map<String, Object> args, Integer line, boolean blocking) {
    }

    public record CheckResult(boolean valid, List<Issue> issues) {
    }

    public record VersionSummary(
        Long id,
        Integer versionNumber,
        InventoryLoanTemplateOrigin origin,
        String defaultLabel,
        String fileName,
        String contentHash,
        ZonedDateTime createdAt,
        boolean active
    ) {
    }

    public enum VariableType {
        TEXT,
        FLAG,
        LIST
    }

    public record Variable(String name, VariableType type, String description, String example, List<Variable> fields) {
    }

    public record VariableGroup(String label, String description, List<Variable> variables) {
    }

    public record Limits(long maxFileBytes) {
    }

    public record Overview(
        VersionSummary active,
        String defaultLabel,
        Durations durations,
        long settingsVersion,
        List<VersionSummary> versions,
        List<VariableGroup> variables,
        Limits limits
    ) {
    }
}
