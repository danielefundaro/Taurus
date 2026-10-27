package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public final class InventoryQrDtos {
    private InventoryQrDtos() {
    }

    public enum ScanTarget {ADMIN_ITEM, OWN_ASSIGNMENTS}

    public enum ScanAction {
        ASSIGN,
        RETURN,
        ADD_ITEM_PHOTO,
        REPORT_ISSUE,
        PRINT_LABEL,
        ROTATE_CODE,
        VIEW,
        REQUEST_RETURN,
        COMPLETE_DECISION,
        ADD_RETURN_PHOTO,
        ADD_ISSUE_PHOTO
    }

    public enum LabelLayout {SINGLE_62X40, A4_GRID_3X8}

    /**
     * Administrator view of the scanned item, or the assignments of the member on it: never both.
     *
     * @param overdueAssignments  outstanding assignments past their expiration date
     * @param expiringAssignments outstanding assignments that expire within {@code expiringWithinDays}
     * @param pendingDecisions    outstanding assignments whose current revision waits for the acknowledgment
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScanResponse(
        ScanTarget target,
        Long itemId,
        String inventoryNumber,
        String name,
        InventoryCondition conditionStatus,
        Integer totalQuantity,
        Integer assignedQuantity,
        Integer availableQuantity,
        Long previewPhotoId,
        Long openIssueCount,
        Boolean unsafe,
        Integer overdueAssignments,
        Integer expiringAssignments,
        Integer expiringWithinDays,
        Long pendingDecisions,
        List<ScanAction> allowedActions,
        List<ScanAssignment> assignments
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScanAssignment(
        long assignmentId,
        String inventoryNumber,
        String itemName,
        InventoryCondition conditionStatus,
        int outstandingQuantity,
        InventoryAssignmentStatus status,
        LocalDate expirationDate,
        boolean decisionPending,
        Long previewPhotoId,
        List<ScanAction> allowedActions
    ) {
    }

    public record LabelEntry(@NotNull Long itemId, @Min(1) @Max(20) int copies) {
    }

    public record LabelRequest(
        @NotNull LabelLayout layout,
        @Min(0) @Max(23) int startCell,
        boolean showCutMarks,
        @NotEmpty @Size(max = 100) List<@Valid LabelEntry> entries
    ) {
    }

    public record RotateRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record RotateResponse(int version, ZonedDateTime issuedAt) {
    }

    public record BinaryContent(String fileName, String contentType, byte[] bytes) {
    }
}
