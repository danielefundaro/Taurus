package com.fundaro.zodiac.taurus.service.dto.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Requests and answers of the shares of the participants (docs/event-payout-shares-spec.md). */
public final class PayoutDtos {

    private PayoutDtos() {
    }

    public record SettingRequest(
        @NotNull LocalDate validFrom,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal associationSharePercent,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal roundingStep
    ) {
    }

    public record SettingDTO(
        Long id,
        LocalDate validFrom,
        BigDecimal associationSharePercent,
        BigDecimal roundingStep,
        String createdBy,
        ZonedDateTime createdAt
    ) {
    }

    /** An event already settled that a version with the given date would recalculate. */
    public record SettledEventDTO(Long eventId, String eventName, LocalDate eventDate, BigDecimal settledAmount) {
    }

    /**
     * The factor or the fixed payment of a participant: exactly one of the two, always with a reason. Both are checked
     * by the service, so that each refusal has its own key.
     */
    public record AdjustmentRequest(BigDecimal factor, BigDecimal fixedAmount, @Size(max = 255) String reason) {
    }

    /** Where the money of the event goes, on the planned or on the actual figures. */
    public record DistributionDTO(
        BigDecimal base,
        BigDecimal expenses,
        BigDecimal fixedPayments,
        BigDecimal margin,
        BigDecimal fullAssociationShare,
        BigDecimal associationShare,
        boolean associationShareReduced,
        BigDecimal net,
        BigDecimal baseShare,
        BigDecimal sharesTotal,
        BigDecimal residual,
        BigDecimal deficit,
        BigDecimal effectiveAssociationShare,
        int sharingParticipants
    ) {
    }

    /**
     * A participant of the event. {@code kind} is {@code SHARE} or {@code FIXED}; a user who is no longer present but
     * was paid keeps a line, with {@code present} false, so that what was paid stays visible as a deviation.
     */
    public record EventLineDTO(
        Long userId,
        String name,
        boolean present,
        String kind,
        BigDecimal factor,
        BigDecimal fixedAmount,
        String reason,
        BigDecimal simulatedTheoreticalShare,
        BigDecimal simulatedShare,
        BigDecimal realTheoreticalShare,
        BigDecimal realShare,
        BigDecimal settled,
        BigDecimal due,
        BigDecimal deviation
    ) {
    }

    public record EventPayoutDTO(
        Long eventId,
        String eventName,
        LocalDate eventDate,
        Long settingId,
        LocalDate settingValidFrom,
        BigDecimal savedSharePercent,
        BigDecimal savedRoundingStep,
        BigDecimal sharePercent,
        BigDecimal roundingStep,
        boolean preview,
        boolean incomeRecorded,
        DistributionDTO planned,
        DistributionDTO actual,
        List<EventLineDTO> lines,
        BigDecimal settledTotal,
        BigDecimal dueTotal,
        BigDecimal deviationTotal
    ) {
    }

    /** A member with shares or fixed payments, and the totals the selection of the member starts from. */
    public record MemberSummaryDTO(Long userId, String name, int eventCount, BigDecimal dueTotal, BigDecimal settledTotal, BigDecimal deviationTotal) {
    }

    /**
     * A share or a fixed payment of a member in an event. {@code configured} is false when no version of the parameters
     * applies to the date of the event: the share is then unknown and zero, while a fixed payment stays due.
     */
    public record MemberLineDTO(
        Long eventId,
        String eventName,
        LocalDate eventDate,
        boolean present,
        boolean configured,
        boolean incomeRecorded,
        String kind,
        BigDecimal factor,
        BigDecimal fixedAmount,
        String reason,
        BigDecimal simulatedShare,
        BigDecimal realShare,
        BigDecimal settled,
        BigDecimal due,
        BigDecimal deviation
    ) {
    }

    public record MemberPayoutsDTO(
        Long userId,
        String name,
        BigDecimal dueTotal,
        BigDecimal settledTotal,
        BigDecimal deviationTotal,
        List<MemberLineDTO> lines
    ) {
    }

    public record SettlementLineRequest(
        @NotNull Long eventId,
        @NotNull Long userId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount
    ) {
    }

    /** One expense movement for one member, with a line for each event it pays. */
    public record SettlementRequest(
        @NotNull Long accountId,
        Long categoryId,
        @NotNull LocalDate bookingDate,
        @Size(max = 4000) String description,
        @Size(max = 10000) String notes,
        UUID requestKey,
        List<@Valid @NotNull SettlementLineRequest> lines
    ) {
    }

    public record SettlementLineDTO(Long id, Long eventId, String eventName, Long userId, BigDecimal amount) {
    }

    public record SettlementDTO(
        Long movementId,
        Long userId,
        String userName,
        LocalDate bookingDate,
        BigDecimal total,
        int eventCount,
        List<SettlementLineDTO> lines
    ) {
    }
}
