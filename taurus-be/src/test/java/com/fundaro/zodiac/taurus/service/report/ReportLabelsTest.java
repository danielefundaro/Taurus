package com.fundaro.zodiac.taurus.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategoryDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import org.junit.jupiter.api.Test;

class ReportLabelsTest {

    @Test
    void translatesFinanceValuesWithoutChangingDomainEnums() {
        assertThat(ReportLabels.financialDirection(FinancialDirection.INCOME)).isEqualTo("Entrata");
        assertThat(ReportLabels.financialDirection(FinancialDirection.EXPENSE)).isEqualTo("Uscita");
        assertThat(ReportLabels.financialCategoryDirection(FinancialCategoryDirection.BOTH)).isEqualTo("Entrata e uscita");
        assertThat(ReportLabels.financialMovementNature(FinancialMovementNature.ORDINARY)).isEqualTo("Ordinaria");
        assertThat(ReportLabels.financialMovementNature(FinancialMovementNature.OPENING)).isEqualTo("Apertura");
        assertThat(ReportLabels.financialMovementNature(FinancialMovementNature.TRANSFER)).isEqualTo("Trasferimento");
        assertThat(ReportLabels.accountingYearStatus(AccountingYearStatus.OPEN)).isEqualTo("Da generare");
        assertThat(ReportLabels.accountingYearStatus(AccountingYearStatus.ROLLED_OVER)).isEqualTo("Generato");
        assertThat(ReportLabels.economicStatus("OVERPAID_OR_OVERRUN")).isEqualTo("Preventivo superato");
    }

    @Test
    void translatesInventoryValuesWithoutChangingDomainEnums() {
        assertThat(ReportLabels.inventoryCondition(InventoryCondition.GOOD)).isEqualTo("Buono");
        assertThat(ReportLabels.inventoryAssignmentStatus(InventoryAssignmentStatus.PARTIALLY_RETURNED))
            .isEqualTo("Parzialmente riconsegnata");
        assertThat(ReportLabels.inventoryDecision(InventoryDecisionType.ACCEPTED)).isEqualTo("accettata");
        assertThat(ReportLabels.inventoryReturnStatus(InventoryReturnStatus.COMPLETED)).isEqualTo("completata");
    }
}
