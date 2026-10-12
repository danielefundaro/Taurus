package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanReturnOutcome;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Line;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.RegisterEntry;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Data of a loan sheet before layout: what Taurus knows about the sheet, independent of the template that prints it.
 */
public record LoanSheetContent(
    boolean preview,
    Tenant tenant,
    String sheetNumber,
    Durations durations,
    InventoryLoanHolderRole holderRole,
    String memberName,
    String memberLastName,
    LocalDate memberBirthDate,
    String memberEmail,
    List<Line> lines,
    LocalDate issuedOn,
    String place,
    String authorizationReference,
    LocalDate authorizationDate,
    String associationSignatoryName,
    List<RegisterEntry> register,
    FinalReturn finalReturn
) {

    /**
     * Data of the tenant; {@code logo} is a {@code data:} URI, or {@code null} when it is missing or not needed.
     */
    public record Tenant(
        String name,
        String address,
        String postalCode,
        String city,
        String province,
        String email,
        String phone,
        String pec,
        String vatNumber,
        String taxCode,
        String logo
    ) {
    }

    public record FinalReturnRow(String itemName, String serialOrInventory, String returned, String condition, String notes) {
    }

    public record FinalReturn(
        LocalDate date,
        List<FinalReturnRow> rows,
        InventoryLoanReturnOutcome outcome,
        BigDecimal chargeAmount,
        LocalDate chargeDueDate,
        String receivedByName
    ) {
    }
}
