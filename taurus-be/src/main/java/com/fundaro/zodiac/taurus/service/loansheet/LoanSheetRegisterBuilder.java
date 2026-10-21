package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRenewal;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetLine;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturn;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnReason;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.RegisterEntry;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturn;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturnRow;
import com.fundaro.zodiac.taurus.service.report.ReportLabels;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derives the register of variations (section 8) and the final return report (section 9) from what Taurus recorded
 * after the sheet was issued.
 *
 * <p>A completed return is final when the sheet is closed and the return was completed on the day of the last
 * completed return of the sheet; every other completed return is a partial return of the register.
 */
public final class LoanSheetRegisterBuilder {

    private LoanSheetRegisterBuilder() {
    }

    /**
     * The register with, at the same positions, the origin of each row: {@link #assignmentKey}, {@link #renewalKey}
     * or {@link #returnKey}.
     */
    public record Result(List<RegisterEntry> register, List<String> keys, List<FinalReturnRow> finalRows, LocalDate finalDate) {
    }

    private record Timed(ZonedDateTime at, int order, String key, RegisterEntry entry) {
    }

    public static String assignmentKey(Long assignmentId) {
        return "assignment:" + assignmentId;
    }

    public static String renewalKey(Long renewalId) {
        return "renewal:" + renewalId;
    }

    public static String returnKey(Long returnId) {
        return "return:" + returnId;
    }

    public static Result build(
        InventoryLoanSheet sheet,
        List<InventoryLoanSheetLine> lines,
        List<InventoryAssignment> assignments,
        List<InventoryReturn> completedReturns,
        List<InventoryAssignmentRenewal> renewals,
        Map<Long, LocalDate> deliveryExpirations,
        ZoneId zone
    ) {
        Map<Long, InventoryLoanSheetLine> linesByAssignment = lines.stream()
            .collect(Collectors.toMap(InventoryLoanSheetLine::getAssignmentId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        Map<Long, InventoryAssignment> assignmentsById = assignments.stream()
            .collect(Collectors.toMap(InventoryAssignment::getId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        boolean closed = sheet.getStatus() == InventoryLoanSheetStatus.CLOSED || sheet.getStatus() == InventoryLoanSheetStatus.SETTLED;
        List<InventoryReturn> standardReturns = completedReturns.stream()
            .filter(value -> value.getReason() != InventoryReturnReason.SUBSTITUTION && value.getCompletedAt() != null)
            .toList();
        LocalDate finalDate = closed
            ? standardReturns.stream().map(value -> LoanSheetSupport.localDate(value.getCompletedAt(), zone)).max(Comparator.naturalOrder())
                .orElse(LoanSheetSupport.localDate(sheet.getClosedAt(), zone))
            : null;

        List<Timed> timed = new ArrayList<>();
        for (InventoryAssignment assignment : assignments) {
            if (linesByAssignment.containsKey(assignment.getId())) continue;
            InventoryItem item = assignment.getItem();
            boolean substitution = assignment.getReplacesAssignmentId() != null;
            String label = itemLabel(item.getName(), assignment.getAssignedQuantity());
            if (substitution) label = label + " (sostituisce " + replacedLabel(assignment.getReplacesAssignmentId(), linesByAssignment, assignmentsById) + ")";
            timed.add(new Timed(assignment.getAssignedAt(), 0, assignmentKey(assignment.getId()), new RegisterEntry(
                LoanSheetSupport.localDate(assignment.getAssignedAt(), zone),
                substitution ? "S" : "C",
                label,
                brandModelSerial(item.getBrandModel(), item.getSerialNumber(), item.getInventoryNumber()),
                item.getLoanType() == InventoryLoanType.FIXED_TERM
                    ? Objects.requireNonNullElse(deliveryExpirations.get(assignment.getId()), assignment.getExpirationDate())
                    : null,
                agreedValue(item, assignment.getAssignedQuantity()),
                null
            )));
        }
        for (InventoryAssignmentRenewal renewal : renewals) {
            InventoryAssignment assignment = assignmentsById.get(renewal.getAssignment().getId());
            if (assignment == null) continue;
            InventoryItem item = assignment.getItem();
            timed.add(new Timed(renewal.getInsertDate(), 1, renewalKey(renewal.getId()), new RegisterEntry(
                renewal.getRenewedOn(),
                "N",
                item.getName(),
                brandModelSerial(item.getBrandModel(), item.getSerialNumber(), item.getInventoryNumber()),
                renewal.getNewExpirationDate(),
                agreedValue(item, assignment.getAssignedQuantity()),
                authorization(renewal.getAuthorizationReference(), renewal.getAuthorizationDate())
            )));
        }
        Map<Long, List<InventoryReturn>> finalByAssignment = new LinkedHashMap<>();
        for (InventoryReturn inventoryReturn : standardReturns) {
            InventoryAssignment assignment = assignmentsById.get(inventoryReturn.getAssignment().getId());
            if (assignment == null) continue;
            LocalDate completedOn = LoanSheetSupport.localDate(inventoryReturn.getCompletedAt(), zone);
            if (finalDate != null && finalDate.equals(completedOn)) {
                finalByAssignment.computeIfAbsent(assignment.getId(), key -> new ArrayList<>()).add(inventoryReturn);
                continue;
            }
            InventoryItem item = assignment.getItem();
            timed.add(new Timed(inventoryReturn.getCompletedAt(), 2, returnKey(inventoryReturn.getId()), new RegisterEntry(
                completedOn,
                "R",
                itemLabel(item.getName(), inventoryReturn.getQuantity()),
                brandModelSerial(item.getBrandModel(), item.getSerialNumber(), item.getInventoryNumber()),
                null,
                null,
                null
            )));
        }
        timed.sort(Comparator.comparing(Timed::at, Comparator.nullsLast(Comparator.naturalOrder())).thenComparingInt(Timed::order));

        List<FinalReturnRow> finalRows = new ArrayList<>();
        finalByAssignment.forEach((assignmentId, returns) -> {
            InventoryAssignment assignment = assignmentsById.get(assignmentId);
            InventoryItem item = assignment.getItem();
            InventoryReturn last = returns.get(returns.size() - 1);
            String notes = returns.stream().map(InventoryReturn::getNotes).map(LoanSheetText::blankToNull).filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
            finalRows.add(new FinalReturnRow(
                itemLabel(item.getName(), returns.stream().mapToInt(InventoryReturn::getQuantity).sum()),
                LoanSheetText.serialOrInventory(item.getSerialNumber(), item.getInventoryNumber()),
                assignment.getOutstandingQuantity() == 0 ? "Sì" : "No (mancano " + assignment.getOutstandingQuantity() + ")",
                last.getReturnCondition() == null ? null : ReportLabels.inventoryCondition(last.getReturnCondition()),
                notes.isEmpty() ? null : notes
            ));
        });
        return new Result(timed.stream().map(Timed::entry).toList(), timed.stream().map(Timed::key).toList(), finalRows, finalDate);
    }

    public static FinalReturn finalReturn(InventoryLoanSheet sheet, Result result) {
        if (result.finalDate() == null || sheet.getReturnOutcome() == null) return null;
        return new FinalReturn(
            result.finalDate(),
            result.finalRows(),
            sheet.getReturnOutcome(),
            sheet.getChargeAmount(),
            sheet.getChargeDueDate(),
            sheet.getReceivedByName()
        );
    }

    public static String authorization(String reference, LocalDate date) {
        return "Delibera n. " + reference + "\ndel " + LoanSheetText.date(date);
    }

    private static String agreedValue(InventoryItem item, int quantity) {
        return LoanSheetText.agreedValue(LoanSheetText.euroUnitValue(item.getEstimatedUnitValue(), item.getCurrency()), quantity);
    }

    static String itemLabel(String name, int quantity) {
        return quantity > 1 ? name + " (" + quantity + ")" : name;
    }

    static String brandModelSerial(String brandModel, String serialNumber, String inventoryNumber) {
        return LoanSheetText.join(", ", brandModel, LoanSheetText.serialOrInventory(serialNumber, inventoryNumber));
    }

    private static String replacedLabel(
        Long replacedId,
        Map<Long, InventoryLoanSheetLine> linesByAssignment,
        Map<Long, InventoryAssignment> assignmentsById
    ) {
        InventoryLoanSheetLine line = linesByAssignment.get(replacedId);
        if (line != null) return "n. " + line.getLineNumber();
        InventoryAssignment replaced = assignmentsById.get(replacedId);
        return replaced == null ? "bene precedente" : replaced.getItem().getName();
    }
}
