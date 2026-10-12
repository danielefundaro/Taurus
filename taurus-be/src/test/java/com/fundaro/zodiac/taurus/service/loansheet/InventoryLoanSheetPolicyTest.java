package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.ExpirationRange;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryLoanSheetPolicyTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Mock
    InventoryLoanSheetRepository sheetRepository;
    @Mock
    InventoryAssignmentRepository assignmentRepository;
    @Mock
    TenantTimeZoneService tenantTimeZoneService;
    @Mock
    InventoryLoanTemplateService templateService;

    private InventoryLoanSheetPolicy policy;
    private InventoryLoanSheet sheet;

    @BeforeEach
    void setUp() {
        policy = new InventoryLoanSheetPolicy(sheetRepository, assignmentRepository, tenantTimeZoneService, templateService);
        sheet = new InventoryLoanSheet();
        sheet.setId(100L);
        sheet.setSheetNumber("2026/002");
        sheet.setStatus(InventoryLoanSheetStatus.ISSUED);
        sheet.setMinMonths(12);
        sheet.setMaxMonths(24);
        sheet.setRenewalMaxMonths(24);
        sheet.setTotalMaxMonths(36);
        when(tenantTimeZoneService.currentZoneId()).thenReturn(ROME);
        when(sheetRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sheet));
        // The current rules of the tenant differ from those registered on the sheet.
        InventoryLoanSettings settings = new InventoryLoanSettings();
        settings.setMinMonths(6);
        settings.setMaxMonths(18);
        settings.setRenewalMaxMonths(12);
        settings.setTotalMaxMonths(30);
        when(templateService.settings(anyString())).thenReturn(settings);
    }

    @Test
    void linksNewAssignmentsToTheIssuedSheetAfterCheckingTheLoanRules() {
        InventoryItem instrument = item(InventoryLoanType.FIXED_TERM);
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, ROME);
        when(sheetRepository.findFirstByUserIndexAndStatusAndDeletedFalse(42L, InventoryLoanSheetStatus.ISSUED)).thenReturn(Optional.of(sheet));

        assertThat(policy.sheetForNewAssignment(42L, instrument, now, LocalDate.of(2028, 10, 1), "admin")).contains(sheet);
        assertThatThrownBy(() -> policy.sheetForNewAssignment(42L, instrument, now, null, "admin"))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanSheet.missingExpiration");
                assertThat(exception.getArgs().values()).containsEntry("sheetNumber", "2026/002");
            });
    }

    @Test
    void checksFixedTermLoansAgainstTheTenantRulesWhenTheMemberHasNoIssuedSheet() {
        InventoryItem instrument = item(InventoryLoanType.FIXED_TERM);
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, ROME);

        assertThat(policy.sheetForNewAssignment(42L, instrument, now, LocalDate.of(2027, 10, 1), "admin")).isEmpty();
        assertThatThrownBy(() -> policy.sheetForNewAssignment(42L, instrument, now, null, "admin"))
            .isInstanceOfSatisfying(RequestAlertException.class, exception ->
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanDuration.missingExpiration"));
        // 24 months would fit the sheet rules, but the tenant now allows at most 18.
        assertThatThrownBy(() -> policy.sheetForNewAssignment(42L, instrument, now, LocalDate.of(2028, 10, 1), "admin"))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanDuration.expirationOutOfRange");
                assertThat(exception.getArgs().values())
                    .containsEntry("minimum", "2027-04-01")
                    .containsEntry("maximum", "2028-04-01")
                    .doesNotContainKey("sheetNumber");
            });
    }

    @Test
    void leavesTheExpirationOfOtherLoansFree() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, ROME);

        assertThat(policy.sheetForNewAssignment(42L, item(null), now, null, "admin")).isEmpty();
        assertThat(policy.sheetForNewAssignment(42L, item(InventoryLoanType.PRECARIOUS), now, LocalDate.of(2026, 10, 2), "admin")).isEmpty();
    }

    @Test
    void checksAChangedExpirationOfAnUnlinkedFixedTermLoanFromTheAssignmentDay() {
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setItem(item(InventoryLoanType.FIXED_TERM));
        assignment.setUserIndex(42L);
        assignment.setAssignedQuantity(1);
        assignment.setAssignedAt(ZonedDateTime.of(2026, 1, 15, 9, 0, 0, 0, ROME));
        assignment.setExpirationDate(LocalDate.of(2026, 3, 1));

        // The expiration registered earlier is not checked again when it does not change.
        assertThatCode(() -> policy.checkAssignmentUpdate(assignment, 2, LocalDate.of(2026, 3, 1), "admin")).doesNotThrowAnyException();
        assertThatCode(() -> policy.checkAssignmentUpdate(assignment, 1, LocalDate.of(2027, 1, 15), "admin")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.checkAssignmentUpdate(assignment, 1, LocalDate.of(2026, 6, 1), "admin"))
            .isInstanceOfSatisfying(RequestAlertException.class, exception ->
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanDuration.expirationOutOfRange"));
    }

    @Test
    void exposesTheExpirationRangeOfTheRulesThatApply() {
        when(sheetRepository.findFirstByUserIndexAndStatusAndDeletedFalse(42L, InventoryLoanSheetStatus.ISSUED)).thenReturn(Optional.of(sheet));

        assertThat(policy.expirationRange(42L, LocalDate.of(2026, 1, 31), "admin"))
            .isEqualTo(new ExpirationRange(LocalDate.of(2026, 1, 31), LocalDate.of(2027, 1, 31), LocalDate.of(2028, 1, 31), "2026/002"));
        assertThat(policy.expirationRange(7L, LocalDate.of(2026, 1, 31), "admin"))
            .isEqualTo(new ExpirationRange(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 7, 31), LocalDate.of(2027, 7, 31), null));
        assertThat(policy.expirationRange(7L, null, "admin").deliveredOn()).isEqualTo(LocalDate.now(ROME));
    }

    @Test
    void locksExpirationQuantityAndDeletionOfLinkedAssignments() {
        InventoryAssignment assignment = linkedAssignment();

        assertThatThrownBy(() -> policy.checkAssignmentUpdate(assignment, 1, LocalDate.of(2029, 1, 1), "admin"))
            .isInstanceOf(RequestAlertException.class).hasMessageContaining("solo con un rinnovo");
        assertThatThrownBy(() -> policy.checkAssignmentUpdate(assignment, 2, assignment.getExpirationDate(), "admin"))
            .isInstanceOf(RequestAlertException.class).hasMessageContaining("non è modificabile");
        assertThatCode(() -> policy.checkAssignmentUpdate(assignment, 1, assignment.getExpirationDate(), "admin")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.checkAssignmentDeletion(assignment))
            .isInstanceOf(RequestAlertException.class).hasMessageContaining("non può essere eliminata");
    }

    @Test
    void allowsCompletedReturnDeletionOnlyWhileTheSheetIsIssued() {
        InventoryAssignment assignment = linkedAssignment();

        assertThatCode(() -> policy.checkCompletedReturnDeletion(assignment)).doesNotThrowAnyException();
        sheet.setStatus(InventoryLoanSheetStatus.CLOSED);
        assertThatThrownBy(() -> policy.checkCompletedReturnDeletion(assignment)).isInstanceOf(RequestAlertException.class);
    }

    @Test
    void releasesCancelledSheets() {
        InventoryAssignment assignment = linkedAssignment();
        sheet.setStatus(InventoryLoanSheetStatus.CANCELLED);

        assertThatCode(() -> policy.checkAssignmentDeletion(assignment)).doesNotThrowAnyException();
    }

    @Test
    void locksTheLoanTypeOfGoodsInAnIssuedSheet() {
        InventoryItem item = item(InventoryLoanType.FIXED_TERM);
        item.setId(7L);
        when(assignmentRepository.existsForItemInLoanSheets(eq(7L), any())).thenReturn(true);

        assertThatThrownBy(() -> policy.checkItemUpdate(item, InventoryLoanType.PRECARIOUS))
            .isInstanceOf(RequestAlertException.class).hasMessageContaining("regime di comodato");
        assertThatCode(() -> policy.checkItemUpdate(item, InventoryLoanType.FIXED_TERM)).doesNotThrowAnyException();
        when(assignmentRepository.existsForItemInLoanSheets(eq(7L), any())).thenReturn(false);
        assertThatCode(() -> policy.checkItemUpdate(item, null)).doesNotThrowAnyException();
    }

    private InventoryAssignment linkedAssignment() {
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setItem(item(InventoryLoanType.FIXED_TERM));
        assignment.setAssignedQuantity(1);
        assignment.setExpirationDate(LocalDate.of(2028, 9, 28));
        assignment.setLoanSheetId(100L);
        return assignment;
    }

    private static InventoryItem item(InventoryLoanType loanType) {
        InventoryItem item = new InventoryItem();
        item.setName("Clarinetto");
        item.setLoanType(loanType);
        return item;
    }
}
