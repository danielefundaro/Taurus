package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanReturnOutcome;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Line;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.RegisterEntry;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturn;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.FinalReturnRow;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fictitious data used by the previews of a template, so that the tenant can check variables and layout.
 */
public final class LoanSheetSamples {

    private LoanSheetSamples() {
    }

    public static LoanSheetContent preview(Tenant tenant, Durations parameters, LocalDate today) {
        LocalDate delivery = today;
        LocalDate expiration = delivery.plusMonths(parameters.maxMonths());
        List<Line> lines = List.of(
            new Line(1, null, "Clarinetto in Si bemolle", "Yamaha YCL-255", "SN-004512", "INV-0001", "Custodia, bocchino, legatura",
                1, new BigDecimal("650.00"), InventoryCondition.GOOD, "Lievi segni d’uso sulla campana", InventoryLoanType.FIXED_TERM, expiration),
            new Line(2, null, "Giacca della divisa", null, null, "DIV-0112", "Taglia 48", 1, new BigDecimal("120.00"),
                InventoryCondition.EXCELLENT, null, InventoryLoanType.PRECARIOUS, null),
            new Line(3, null, "Cappello della divisa", null, null, "DIV-0201", "Taglia 57", 1, new BigDecimal("35.00"),
                InventoryCondition.NEW, null, InventoryLoanType.PRECARIOUS, null)
        );
        List<RegisterEntry> register = List.of(
            new RegisterEntry(delivery.plusMonths(2), "C", "Leggio pieghevole", "INV-0340", null, "25,00", null),
            new RegisterEntry(delivery.plusMonths(parameters.maxMonths()).minusMonths(1), "N", "Clarinetto in Si bemolle",
                "Yamaha YCL-255, SN-004512", expiration.plusMonths(Math.min(12, parameters.renewalMaxMonths())), "650,00",
                LoanSheetRegisterBuilder.authorization("12", delivery.plusMonths(parameters.maxMonths()).minusMonths(2)))
        );
        FinalReturn finalReturn = new FinalReturn(
            expiration,
            List.of(new FinalReturnRow("Clarinetto in Si bemolle", "SN-004512 / INV-0001", "Sì", "Buono", null)),
            InventoryLoanReturnOutcome.CHARGES,
            new BigDecimal("45.00"),
            expiration.plusDays(30),
            "Mario Bianchi"
        );
        return new LoanSheetContent(
            true,
            tenant,
            null,
            parameters,
            InventoryLoanHolderRole.GUARDIAN,
            "Giulia",
            "Rossi",
            today.minusYears(14),
            "giulia.rossi@example.org",
            lines,
            delivery,
            tenant.city(),
            "7",
            delivery.minusDays(10),
            "Mario Bianchi",
            register,
            finalReturn
        );
    }
}
