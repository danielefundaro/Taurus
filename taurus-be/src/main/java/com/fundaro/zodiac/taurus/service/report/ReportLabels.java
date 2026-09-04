package com.fundaro.zodiac.taurus.service.report;

import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategoryDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;

public final class ReportLabels {

    private ReportLabels() {}

    public static String financialDirection(FinancialDirection value) {
        if (value == null) return "-";
        return switch (value) {
            case INCOME -> "Entrata";
            case EXPENSE -> "Uscita";
        };
    }

    public static String financialCategoryDirection(FinancialCategoryDirection value) {
        if (value == null) return "-";
        return switch (value) {
            case INCOME -> "Entrata";
            case EXPENSE -> "Uscita";
            case BOTH -> "Entrata e uscita";
        };
    }

    public static String financialMovementNature(FinancialMovementNature value) {
        if (value == null) return "-";
        return switch (value) {
            case ORDINARY -> "Ordinaria";
            case OPENING -> "Apertura";
            case TRANSFER -> "Trasferimento";
        };
    }

    public static String accountingYearStatus(AccountingYearStatus value) {
        if (value == null) return "Non disponibile";
        return switch (value) {
            case OPEN -> "Da generare";
            case ROLLED_OVER -> "Generato";
        };
    }

    public static String economicStatus(String value) {
        if (value == null || value.isBlank()) return "Non disponibile";
        return switch (value) {
            case "NO_BUDGET" -> "Nessun preventivo";
            case "NO_MOVEMENTS" -> "Nessun movimento";
            case "UNPLANNED_MOVEMENTS" -> "Movimenti non preventivati";
            case "PARTIALLY_SETTLED" -> "Parzialmente saldato";
            case "OVERPAID_OR_OVERRUN" -> "Preventivo superato";
            case "SETTLED" -> "Saldato";
            default -> "Stato non riconosciuto";
        };
    }

    public static String inventoryCondition(InventoryCondition value) {
        if (value == null) return "Non disponibile";
        return switch (value) {
            case NEW -> "Nuovo";
            case EXCELLENT -> "Eccellente";
            case GOOD -> "Buono";
            case FAIR -> "Discreto";
            case TO_REPAIR -> "Da riparare";
            case OUT_OF_SERVICE -> "Fuori servizio";
        };
    }

    public static String inventoryAssignmentStatus(InventoryAssignmentStatus value) {
        if (value == null) return "Non disponibile";
        return switch (value) {
            case ACTIVE -> "Attiva";
            case PARTIALLY_RETURNED -> "Parzialmente riconsegnata";
            case RETURNED -> "Riconsegnata";
            case CANCELLED -> "Annullata";
        };
    }

    public static String inventoryDecision(InventoryDecisionType value) {
        if (value == null) return "non disponibile";
        return switch (value) {
            case ACCEPTED -> "accettata";
            case REJECTED -> "rifiutata";
        };
    }

    public static String inventoryReturnStatus(InventoryReturnStatus value) {
        if (value == null) return "non disponibile";
        return switch (value) {
            case REQUESTED -> "richiesta";
            case COMPLETED -> "completata";
            case CANCELLED -> "annullata";
        };
    }
}
