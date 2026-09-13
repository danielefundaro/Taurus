package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;
import java.util.List;

public final class InventoryIssueDtos {
    private InventoryIssueDtos() {
    }

    public record CreateRequest(
        @Min(1) int reportedQuantity,
        @NotNull InventoryIssueSeverity severity,
        @NotBlank @Size(max = 2000) String description
    ) {
    }

    public record TransitionRequest(
        @NotNull InventoryIssueStatus status,
        @Size(max = 2000) String resolutionNotes,
        InventoryCondition itemConditionStatus,
        @Min(0) long version
    ) {
    }

    public record Photo(long id, String fileName, String contentType, long fileSize, int displayOrder) {
    }

    public record Response(
        long id,
        long itemId,
        Long assignmentId,
        String inventoryNumber,
        String itemName,
        int reportedQuantity,
        InventoryIssueSeverity severity,
        String description,
        InventoryIssueStatus status,
        String resolutionNotes,
        ZonedDateTime reportedAt,
        ZonedDateTime acknowledgedAt,
        ZonedDateTime resolvedAt,
        long version,
        List<Photo> photos
    ) {
    }
}
