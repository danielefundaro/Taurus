package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanReturnOutcome;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public final class InventoryLoanSheetDtos {

    private InventoryLoanSheetDtos() {
    }

    public record Summary(
        Long id,
        String sheetNumber,
        InventoryLoanSheetStatus status,
        LocalDate issuedOn,
        Long userIndex,
        String memberName,
        String memberLastName,
        InventoryLoanHolderRole holderRole,
        String templateLabel,
        InventoryLoanReturnOutcome returnOutcome,
        ZonedDateTime closedAt,
        ZonedDateTime settledAt,
        ZonedDateTime cancelledAt,
        boolean personalDataPurged,
        boolean closable,
        long entityVersion
    ) {
    }

    public record Line(
        int lineNumber,
        Long assignmentId,
        String itemName,
        String brandModel,
        String serialNumber,
        String inventoryNumber,
        String accessories,
        int quantity,
        BigDecimal agreedUnitValue,
        InventoryCondition conditionStatus,
        String conditionNotes,
        InventoryLoanType loanType,
        LocalDate expirationDate
    ) {
    }

    public record RegisterEntry(
        LocalDate date,
        String operation,
        String itemName,
        String brandModelSerial,
        LocalDate expirationDate,
        String agreedValue,
        String authorization
    ) {
    }

    public record LinkedAssignment(
        Long assignmentId,
        Long itemId,
        String itemName,
        String inventoryNumber,
        InventoryLoanType loanType,
        int assignedQuantity,
        int outstandingQuantity,
        InventoryAssignmentStatus status,
        LocalDate expirationDate,
        boolean variation,
        Long replacesAssignmentId
    ) {
    }

    public record Detail(
        Summary summary,
        String place,
        String authorizationReference,
        LocalDate authorizationDate,
        String associationSignatoryName,
        BigDecimal chargeAmount,
        LocalDate chargeDueDate,
        String receivedByName,
        String cancellationReason,
        Terms terms,
        List<Line> lines,
        List<RegisterEntry> register,
        List<LinkedAssignment> assignments
    ) {
    }

    /**
     * A delivery that breaks the duration rules: {@code code} completes the key {@code inventory.loanDuration.<code>}.
     */
    public record Issue(Long assignmentId, String code, Map<String, Object> args) {
    }

    /**
     * Template and duration rules of a sheet, or of the tenant for a sheet still to be issued.
     */
    public record Terms(Long templateId, String templateLabel, int minMonths, int maxMonths, int renewalMaxMonths, int totalMaxMonths) {
    }

    /**
     * Expiration dates allowed for a fixed-term loan delivered on {@code deliveredOn}; {@code sheetNumber} is the issued
     * sheet whose rules apply, null when the current rules of the tenant apply.
     */
    public record ExpirationRange(LocalDate deliveredOn, LocalDate minimum, LocalDate maximum, String sheetNumber) {
    }

    public record ProposalAssignment(
        Long assignmentId,
        Long itemId,
        String itemName,
        String inventoryNumber,
        InventoryLoanType loanType,
        int outstandingQuantity,
        LocalDate assignedOn,
        LocalDate expirationDate,
        List<Issue> issues
    ) {
    }

    public record Proposal(
        Long userIndex,
        String memberName,
        String memberLastName,
        LocalDate memberBirthDate,
        InventoryLoanHolderRole suggestedRole,
        boolean roleRequired,
        LocalDate issuedOn,
        String place,
        String associationSignatoryName,
        Long openSheetId,
        String openSheetNumber,
        Terms terms,
        List<ProposalAssignment> assignments
    ) {
    }

    public record IssueRequest(
        @NotEmpty @Size(max = 200) List<@NotNull Long> assignmentIds,
        @NotNull LocalDate issuedOn,
        @Size(max = 255) String place,
        InventoryLoanHolderRole holderRole,
        @Size(max = 128) String authorizationReference,
        LocalDate authorizationDate,
        @Size(max = 255) String associationSignatoryName
    ) {
    }

    public record CloseRequest(
        @NotNull InventoryLoanReturnOutcome outcome,
        @DecimalMin("0.01") BigDecimal chargeAmount,
        LocalDate chargeDueDate,
        @Size(max = 255) String receivedByName
    ) {
    }

    public record CancelRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record RenewalRequest(
        @NotNull LocalDate newExpirationDate,
        @NotBlank @Size(max = 128) String authorizationReference,
        @NotNull LocalDate authorizationDate,
        LocalDate renewedOn
    ) {
    }

    public record SubstitutionRequest(
        @NotNull Long itemId,
        @Min(1) int quantity,
        LocalDate expirationDate,
        @Size(max = 500) String accessories,
        String description,
        InventoryCondition returnCondition,
        @Size(max = 2000) String returnNotes
    ) {
    }

    public record PdfContent(String fileName, byte[] bytes) {
    }
}
