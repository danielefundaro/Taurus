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
    FinalReturn finalReturn,
    Signature signature
) {

    /**
     * Content of a sheet printed on paper, or of a copy for consultation.
     */
    public LoanSheetContent(
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
        this(preview, tenant, sheetNumber, durations, holderRole, memberName, memberLastName, memberBirthDate, memberEmail, lines, issuedOn, place,
            authorizationReference, authorizationDate, associationSignatoryName, register, finalReturn, null);
    }

    /**
     * An act signed electronically: {@code act} is one of the values of {@link LoanSheetActs}, {@code holder} the data
     * entered by the holder before signing the issue, {@code graceDays} the days the signed acts stay downloadable.
     */
    public record Signature(String act, HolderData holder, int graceDays) {
    }

    /**
     * Data of sections 1 and 2 entered by the holder instead of filling in the paper form. A parent or guardian enters
     * also the own name and date of birth; {@code holderEmail} is the address registered at issue.
     */
    public record HolderData(
        String birthPlace,
        String residence,
        String postalCode,
        String cityProvince,
        String taxCode,
        String document,
        String documentIssuedBy,
        LocalDate documentIssuedOn,
        String phone,
        String minorBirthPlace,
        String holderName,
        LocalDate holderBirthDate,
        String holderEmail
    ) {
        /**
         * The data entered by the adult member.
         */
        public HolderData(
            String birthPlace,
            String residence,
            String postalCode,
            String cityProvince,
            String taxCode,
            String document,
            String documentIssuedBy,
            LocalDate documentIssuedOn,
            String phone,
            String minorBirthPlace
        ) {
            this(birthPlace, residence, postalCode, cityProvince, taxCode, document, documentIssuedBy, documentIssuedOn, phone, minorBirthPlace, null, null, null);
        }
    }

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
