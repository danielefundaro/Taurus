package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.RegisterEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoanSheetRegisterBuilderTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    private static final ZonedDateTime ISSUE = ZonedDateTime.of(2026, 9, 28, 10, 0, 0, 0, ROME);

    @Test
    void listsLaterDeliveriesSubstitutionsRenewalsAndPartialReturnsInOrder() {
        InventoryLoanSheet sheet = sheet(InventoryLoanSheetStatus.ISSUED);
        InventoryAssignment instrument = assignment(1L, item(10L, "Clarinetto", InventoryLoanType.FIXED_TERM, "SN-7"), 1, ISSUE, LocalDate.of(2027, 9, 28));
        InventoryAssignment uniform = assignment(2L, item(20L, "Giacca", InventoryLoanType.PRECARIOUS, null), 2, ISSUE, null);
        InventoryAssignment stand = assignment(3L, item(30L, "Leggio", InventoryLoanType.PRECARIOUS, null), 1, ISSUE.plusMonths(1), null);
        InventoryAssignment replacement = assignment(4L, item(40L, "Clarinetto nuovo", InventoryLoanType.FIXED_TERM, "SN-9"), 1, ISSUE.plusMonths(3), LocalDate.of(2027, 12, 28));
        replacement.setReplacesAssignmentId(1L);
        InventoryReturn partial = completedReturn(uniform, 1, ISSUE.plusMonths(2), InventoryReturnReason.STANDARD);
        InventoryReturn substitution = completedReturn(instrument, 1, ISSUE.plusMonths(3), InventoryReturnReason.SUBSTITUTION);
        InventoryAssignmentRenewal renewal = renewal(replacement, ISSUE.plusMonths(4), LocalDate.of(2028, 6, 30));

        LoanSheetRegisterBuilder.Result result = LoanSheetRegisterBuilder.build(
            sheet,
            List.of(line(1, 1L), line(2, 2L)),
            List.of(instrument, uniform, stand, replacement),
            List.of(partial, substitution),
            List.of(renewal),
            Map.of(4L, LocalDate.of(2027, 12, 28)),
            ROME
        );

        assertThat(result.register()).extracting(RegisterEntry::operation).containsExactly("C", "R", "S", "N");
        assertThat(result.register().get(0).itemName()).isEqualTo("Leggio");
        assertThat(result.register().get(0).agreedValue()).isEqualTo("100,00");
        assertThat(result.register().get(1).agreedValue()).isNull();
        assertThat(result.register().get(1).itemName()).isEqualTo("Giacca");
        assertThat(result.register().get(2).itemName()).isEqualTo("Clarinetto nuovo (sostituisce n. 1)");
        assertThat(result.register().get(2).brandModelSerial()).isEqualTo("SN-9 / INV-40");
        assertThat(result.register().get(2).expirationDate()).isEqualTo(LocalDate.of(2027, 12, 28));
        assertThat(result.register().get(3).expirationDate()).isEqualTo(LocalDate.of(2028, 6, 30));
        assertThat(result.register().get(3).authorization()).isEqualTo("Delibera n. 12\ndel 20/01/2027");
        assertThat(result.finalRows()).isEmpty();
        assertThat(result.finalDate()).isNull();
    }

    @Test
    void movesTheReturnsOfTheLastDayToTheReturnReportWhenClosed() {
        InventoryLoanSheet sheet = sheet(InventoryLoanSheetStatus.CLOSED);
        sheet.setReturnOutcome(InventoryLoanReturnOutcome.COMPLIANT);
        InventoryAssignment instrument = assignment(1L, item(10L, "Clarinetto", InventoryLoanType.FIXED_TERM, "SN-7"), 1, ISSUE, LocalDate.of(2027, 9, 28));
        InventoryAssignment uniform = assignment(2L, item(20L, "Giacca", InventoryLoanType.PRECARIOUS, null), 2, ISSUE, null);
        instrument.setReturnedQuantity(1);
        uniform.setReturnedQuantity(2);
        InventoryReturn earlier = completedReturn(uniform, 1, ISSUE.plusMonths(2), InventoryReturnReason.STANDARD);
        InventoryReturn lastMorning = completedReturn(uniform, 1, ISSUE.plusMonths(12).withHour(9), InventoryReturnReason.STANDARD);
        InventoryReturn lastEvening = completedReturn(instrument, 1, ISSUE.plusMonths(12).withHour(18), InventoryReturnReason.STANDARD);
        lastEvening.setReturnCondition(InventoryCondition.TO_REPAIR);
        lastEvening.setNotes("Chiave piegata");

        LoanSheetRegisterBuilder.Result result = LoanSheetRegisterBuilder.build(
            sheet,
            List.of(line(1, 1L), line(2, 2L)),
            List.of(instrument, uniform),
            List.of(earlier, lastMorning, lastEvening),
            List.of(),
            Map.of(),
            ROME
        );

        assertThat(result.register()).extracting(RegisterEntry::operation).containsExactly("R");
        assertThat(result.finalDate()).isEqualTo(LocalDate.of(2027, 9, 28));
        assertThat(result.finalRows()).extracting(LoanSheetContent.FinalReturnRow::itemName).containsExactly("Giacca", "Clarinetto");
        assertThat(result.finalRows().get(1).condition()).isEqualTo("Da riparare");
        assertThat(result.finalRows().get(1).notes()).isEqualTo("Chiave piegata");
        assertThat(result.finalRows().get(1).returned()).isEqualTo("Sì");
        assertThat(LoanSheetRegisterBuilder.finalReturn(sheet, result).outcome()).isEqualTo(InventoryLoanReturnOutcome.COMPLIANT);
    }

    private static InventoryLoanSheet sheet(InventoryLoanSheetStatus status) {
        InventoryLoanSheet sheet = new InventoryLoanSheet();
        sheet.setId(100L);
        sheet.setStatus(status);
        sheet.setClosedAt(status == InventoryLoanSheetStatus.CLOSED ? ISSUE.plusMonths(12).withHour(19) : null);
        return sheet;
    }

    private static InventoryItem item(Long id, String name, InventoryLoanType loanType, String serial) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setName(name);
        item.setInventoryNumber("INV-" + id);
        item.setEstimatedUnitValue(new BigDecimal("100"));
        item.setCurrency("EUR");
        item.setLoanType(loanType);
        item.setSerialNumber(serial);
        return item;
    }

    private static InventoryAssignment assignment(Long id, InventoryItem item, int quantity, ZonedDateTime assignedAt, LocalDate expiration) {
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(id);
        assignment.setItem(item);
        assignment.setAssignedQuantity(quantity);
        assignment.setAssignedAt(assignedAt);
        assignment.setExpirationDate(expiration);
        assignment.setLoanSheetId(100L);
        return assignment;
    }

    private static InventoryReturn completedReturn(InventoryAssignment assignment, int quantity, ZonedDateTime completedAt, InventoryReturnReason reason) {
        InventoryReturn value = new InventoryReturn();
        value.setAssignment(assignment);
        value.setQuantity(quantity);
        value.setStatus(InventoryReturnStatus.COMPLETED);
        value.setCompletedAt(completedAt);
        value.setReason(reason);
        return value;
    }

    private static InventoryAssignmentRenewal renewal(InventoryAssignment assignment, ZonedDateTime at, LocalDate newExpiration) {
        InventoryAssignmentRenewal renewal = new InventoryAssignmentRenewal();
        renewal.setAssignment(assignment);
        renewal.setInsertDate(at);
        renewal.setRenewedOn(at.toLocalDate());
        renewal.setNewExpirationDate(newExpiration);
        renewal.setAuthorizationReference("12");
        renewal.setAuthorizationDate(at.toLocalDate().minusDays(8));
        return renewal;
    }

    private static InventoryLoanSheetLine line(int number, Long assignmentId) {
        InventoryLoanSheetLine line = new InventoryLoanSheetLine();
        line.setLineNumber(number);
        line.setAssignmentId(assignmentId);
        return line;
    }
}
