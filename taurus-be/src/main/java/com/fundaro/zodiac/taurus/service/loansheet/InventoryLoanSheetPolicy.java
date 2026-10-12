package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.ExpirationRange;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.fundaro.zodiac.taurus.service.loansheet.LoanSheetSupport.error;

/**
 * Rules the ordinary inventory operations must respect once an assignment belongs to a loan sheet, and the duration
 * rules a fixed-term loan follows from its assignment on.
 */
@Component
@Transactional
public class InventoryLoanSheetPolicy {

    private static final List<InventoryLoanSheetStatus> BINDING_STATUSES = List.of(
        InventoryLoanSheetStatus.ISSUED,
        InventoryLoanSheetStatus.CLOSED,
        InventoryLoanSheetStatus.SETTLED
    );

    private final InventoryLoanSheetRepository sheetRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final InventoryLoanTemplateService templateService;

    public InventoryLoanSheetPolicy(
        InventoryLoanSheetRepository sheetRepository,
        InventoryAssignmentRepository assignmentRepository,
        TenantTimeZoneService tenantTimeZoneService,
        InventoryLoanTemplateService templateService
    ) {
        this.sheetRepository = sheetRepository;
        this.assignmentRepository = assignmentRepository;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.templateService = templateService;
    }

    /**
     * Returns the issued sheet a new assignment of the user joins as a later delivery, after checking the loan rules
     * of that sheet. Without an issued sheet, a fixed-term loan is checked against the current rules of the tenant, so
     * the sheet issued later does not find expirations to fix.
     */
    public Optional<InventoryLoanSheet> sheetForNewAssignment(
        Long userIndex,
        InventoryItem item,
        ZonedDateTime assignedAt,
        LocalDate expirationDate,
        String actor
    ) {
        Optional<InventoryLoanSheet> sheet = issuedSheet(userIndex);
        LocalDate deliveredOn = LoanSheetSupport.localDate(assignedAt, tenantTimeZoneService.currentZoneId());
        sheet.ifPresentOrElse(
            value -> checkDelivery(value, item.getLoanType(), deliveredOn, expirationDate),
            () -> checkFixedTermExpiration(item.getLoanType(), deliveredOn, expirationDate, tenantDurations(actor))
        );
        return sheet;
    }

    /**
     * Range of expiration dates a fixed-term loan of the user may have when delivered on the given day (today when
     * absent): the rules of the issued sheet of the user, otherwise the current rules of the tenant.
     */
    public ExpirationRange expirationRange(Long userIndex, LocalDate deliveredOn, String actor) {
        Optional<InventoryLoanSheet> sheet = issuedSheet(userIndex);
        Durations durations = sheet.map(InventoryLoanTemplateService::durations).orElseGet(() -> tenantDurations(actor));
        LocalDate from = deliveredOn == null ? LocalDate.now(tenantTimeZoneService.currentZoneId()) : deliveredOn;
        return new ExpirationRange(
            from,
            from.plusMonths(durations.minMonths()),
            from.plusMonths(durations.maxMonths()),
            sheet.map(InventoryLoanSheet::getSheetNumber).orElse(null)
        );
    }

    public void checkDelivery(InventoryLoanSheet sheet, InventoryLoanType loanType, LocalDate deliveredOn, LocalDate expirationDate) {
        LoanDurationPolicy.checkDelivery(loanType, deliveredOn, expirationDate, InventoryLoanTemplateService.durations(sheet))
            .ifPresent(violation -> {
                throw error(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Delivery breaks the duration rules of loan sheet " + sheet.getSheetNumber(),
                    "inventory.loanSheet." + violation.code()
                ).with(violation.args()).with("sheetNumber", sheet.getSheetNumber());
            });
    }

    public void checkAssignmentUpdate(InventoryAssignment assignment, int requestedQuantity, LocalDate requestedExpiration, String actor) {
        InventoryLoanSheet sheet = bindingSheet(assignment).orElse(null);
        if (sheet == null) {
            // A changed expiration follows the rules of a new delivery made on the assignment day.
            if (!Objects.equals(assignment.getExpirationDate(), requestedExpiration)) {
                checkFixedTermExpiration(
                    assignment.getItem().getLoanType(),
                    LoanSheetSupport.localDate(assignment.getAssignedAt(), tenantTimeZoneService.currentZoneId()),
                    requestedExpiration,
                    issuedSheet(assignment.getUserIndex()).map(InventoryLoanTemplateService::durations).orElseGet(() -> tenantDurations(actor))
                );
            }
            return;
        }
        if (!Objects.equals(assignment.getExpirationDate(), requestedExpiration)) {
            throw error(
                HttpStatus.CONFLICT,
                "La scadenza di un bene del foglio di affidamento n. " + sheet.getSheetNumber() + " si modifica solo con un rinnovo",
                "inventory.loanSheet.expirationLocked"
            ).with("sheetNumber", sheet.getSheetNumber());
        }
        if (assignment.getAssignedQuantity() != requestedQuantity) {
            throw error(
                HttpStatus.CONFLICT,
                "La quantità di un bene del foglio di affidamento n. " + sheet.getSheetNumber() + " non è modificabile: registrare una nuova consegna o una riconsegna",
                "inventory.loanSheet.quantityLocked"
            ).with("sheetNumber", sheet.getSheetNumber());
        }
    }

    public void checkItemUpdate(InventoryItem item, InventoryLoanType requestedLoanType) {
        if (item.getId() == null || Objects.equals(item.getLoanType(), requestedLoanType)) return;
        if (assignmentRepository.existsForItemInLoanSheets(item.getId(), List.of(InventoryLoanSheetStatus.ISSUED))) {
            throw error(
                HttpStatus.CONFLICT,
                "Il regime di comodato non è modificabile finché l'oggetto è in un foglio di affidamento emesso",
                "inventory.loanSheet.loanTypeLocked"
            );
        }
    }

    public void checkAssignmentDeletion(InventoryAssignment assignment) {
        bindingSheet(assignment).ifPresent(sheet -> {
            throw error(
                HttpStatus.CONFLICT,
                "L'assegnazione fa parte del foglio di affidamento n. " + sheet.getSheetNumber() + " e non può essere eliminata",
                "inventory.loanSheet.assignmentLocked"
            ).with("sheetNumber", sheet.getSheetNumber());
        });
    }

    public void checkCompletedReturnDeletion(InventoryAssignment assignment) {
        bindingSheet(assignment)
            .filter(sheet -> sheet.getStatus() != InventoryLoanSheetStatus.ISSUED)
            .ifPresent(sheet -> {
                throw error(
                    HttpStatus.CONFLICT,
                    "La riconsegna fa parte del foglio di affidamento n. " + sheet.getSheetNumber() + " già chiuso",
                    "inventory.loanSheet.returnLocked"
                ).with("sheetNumber", sheet.getSheetNumber());
            });
    }

    @Transactional(readOnly = true)
    public String sheetNumber(Long sheetId) {
        if (sheetId == null) return null;
        return sheetRepository.findByIdAndDeletedFalse(sheetId).map(InventoryLoanSheet::getSheetNumber).orElse(null);
    }

    private void checkFixedTermExpiration(InventoryLoanType loanType, LocalDate deliveredOn, LocalDate expirationDate, Durations durations) {
        if (loanType != InventoryLoanType.FIXED_TERM) return;
        LoanDurationPolicy.checkDelivery(loanType, deliveredOn, expirationDate, durations)
            .ifPresent(violation -> {
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Delivery breaks the duration rules of the tenant", "inventory.loanDuration." + violation.code())
                    .with(violation.args());
            });
    }

    private Optional<InventoryLoanSheet> issuedSheet(Long userIndex) {
        return sheetRepository.findFirstByUserIndexAndStatusAndDeletedFalse(userIndex, InventoryLoanSheetStatus.ISSUED);
    }

    private Durations tenantDurations(String actor) {
        return InventoryLoanTemplateService.durations(templateService.settings(actor));
    }

    private Optional<InventoryLoanSheet> bindingSheet(InventoryAssignment assignment) {
        if (assignment.getLoanSheetId() == null) return Optional.empty();
        return sheetRepository.findByIdAndDeletedFalse(assignment.getLoanSheetId())
            .filter(sheet -> BINDING_STATUSES.contains(sheet.getStatus()));
    }
}
