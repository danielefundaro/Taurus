package com.fundaro.zodiac.taurus.service.dto.finance;

import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategoryDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record AccountRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull FinancialAccountType accountType,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 34) String iban,
        @Size(max = 255) String bankName,
        Integer displayOrder,
        BigDecimal initialBalance,
        LocalDate initialBalanceDate,
        @Size(max = 4000) String initialBalanceDescription
    ) {
    }

    public record AccountDTO(
        Long id,
        String name,
        String description,
        FinancialAccountType accountType,
        String currency,
        String iban,
        String bankName,
        boolean active,
        int displayOrder,
        BigDecimal balance,
        long movementCount,
        /** Il movimento di apertura del saldo iniziale, a cui il frontend allega il documento che lo giustifica. */
        Long initialBalanceMovementId,
        long version
    ) {
    }

    public record CategoryRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull FinancialCategoryDirection direction,
        Integer displayOrder
    ) {
    }

    public record CategoryDTO(
        Long id,
        String name,
        String description,
        FinancialCategoryDirection direction,
        boolean active,
        boolean systemDefined,
        int displayOrder,
        long version
    ) {
    }

    public record MovementRequest(
        @NotNull Long accountId,
        Long categoryId,
        Long eventId,
        @NotNull FinancialDirection direction,
        @NotNull LocalDate bookingDate,
        LocalDate valueDate,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Size(max = 4000) String description,
        @Size(max = 500) String counterparty,
        @Size(max = 255) String documentReference,
        @Size(max = 10000) String notes,
        UUID requestKey,
        /** Versione letta dal client: se presente, una modifica concorrente viene rifiutata invece di sovrascritta. */
        Long version
    ) {
    }

    public record MovementDTO(
        Long id,
        int accountingYear,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        Long eventId,
        String eventName,
        FinancialDirection direction,
        FinancialMovementNature nature,
        LocalDate bookingDate,
        LocalDate valueDate,
        BigDecimal amount,
        String currency,
        String description,
        String counterparty,
        String documentReference,
        String notes,
        UUID transferGroup,
        boolean reconciled,
        ZonedDateTime reconciledAt,
        String reconciliationReference,
        /** Documenti allegati ancora validi: a zero l'interfaccia segnala il movimento senza giustificativo. */
        long attachmentCount,
        long version
    ) {
    }

    public record ReconciliationRequest(boolean reconciled, @Size(max = 255) String reference) {
    }

    /** L'ordine scelto dall'utente: ogni identificativo prende la posizione che ha nell'elenco. */
    public record ReorderRequest(@NotNull List<Long> ids) {
    }

    public record TransferRequest(
        @NotNull Long sourceAccountId,
        @NotNull Long destinationAccountId,
        @NotNull LocalDate bookingDate,
        LocalDate valueDate,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @NotBlank @Size(max = 4000) String description,
        @Size(max = 10000) String notes,
        UUID requestKey
    ) {
    }

    public record TransferDTO(UUID transferGroup, MovementDTO outgoing, MovementDTO incoming) {
    }

    public record DashboardDTO(
        LocalDate from,
        LocalDate to,
        BigDecimal totalBalance,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal result,
        long movementCount,
        long unreconciledCount,
        /** Posizioni evento ancora da incassare o da pagare: è il lavoro economico che resta aperto. */
        BigDecimal eventsToCollect,
        BigDecimal eventsToPay,
        List<EventEconomicLineDTO> openEvents,
        /** Ultimo riporto generato e suo stato, per sapere se le aperture sono allineate. */
        YearDTO lastRollover,
        List<AccountDTO> accounts
    ) {
    }

    public record AttachmentDTO(Long id, Long movementId, Long mediaAssetId, String fileName, String mimeType,
                                long fileSize, String description) {
    }

    public record YearDTO(
        int year,
        LocalDate startDate,
        LocalDate endDate,
        AccountingYearStatus status,
        ZonedDateTime rolledOverAt,
        ZonedDateTime lastRecalculatedAt
    ) {
    }

    public record EventCostRequest(@NotBlank @Size(max = 4000) String description,
                                   @NotNull @DecimalMin("0.00") BigDecimal amount) {
    }

    public record EventCostDTO(Long id, String description, BigDecimal amount) {
    }

    public record EventBudgetRequest(@DecimalMin("0.00") BigDecimal fee, @NotNull List<@Valid EventCostRequest> costs) {
    }

    public record EventSummaryDTO(
        Long eventId,
        String eventName,
        BigDecimal expectedFee,
        BigDecimal expectedCosts,
        List<EventCostDTO> expectedCostItems,
        BigDecimal expectedMargin,
        BigDecimal received,
        BigDecimal paid,
        BigDecimal actualResult,
        BigDecimal remainingIncome,
        BigDecimal remainingExpense,
        String economicStatus,
        List<MovementDTO> movements
    ) {
    }

    public record StatementLineDTO(MovementDTO movement, BigDecimal balance) {
    }

    public record AccountStatementDTO(
        AccountDTO account,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal closingBalance,
        List<StatementLineDTO> lines
    ) {
    }

    public record CategoryTotalDTO(
        Long categoryId,
        String categoryName,
        FinancialCategoryDirection direction,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        long movementCount
    ) {
    }

    public record AccountYearBalanceDTO(
        Long accountId,
        String accountName,
        BigDecimal openingBalance,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal closingBalance
    ) {
    }

    public record EventEconomicLineDTO(
        Long eventId,
        String eventName,
        LocalDate eventDate,
        BigDecimal expectedFee,
        BigDecimal expectedCosts,
        BigDecimal expectedMargin,
        BigDecimal received,
        BigDecimal paid,
        BigDecimal actualResult,
        BigDecimal remainingIncome,
        BigDecimal remainingExpense,
        String economicStatus
    ) {
    }

    /**
     * Segnalazione informativa su un esercizio: non impedisce nulla, indica soltanto che qualcosa
     * merita un controllo umano.
     */
    public record YearAnomalyDTO(String code, String message, Long movementId, java.util.UUID transferGroup) {
    }

    public record YearSummaryDTO(
        YearDTO year,
        List<AccountYearBalanceDTO> accounts,
        BigDecimal openingTotal,
        BigDecimal ordinaryIncome,
        BigDecimal ordinaryExpense,
        BigDecimal ordinaryResult,
        BigDecimal transferTotal,
        BigDecimal closingTotal,
        List<CategoryTotalDTO> categories,
        List<EventEconomicLineDTO> openEvents,
        long unreconciledCount,
        BigDecimal unreconciledAmount,
        List<YearAnomalyDTO> anomalies,
        ZonedDateTime lastRecalculatedAt
    ) {
    }


    public record EventStatementDTO(
        EventSummaryDTO summary,
        BigDecimal feeVariance,
        BigDecimal costVariance,
        BigDecimal resultVariance
    ) {
    }
}
