package com.fundaro.zodiac.taurus.service.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * The distribution of the base of an event among its participants (docs/event-payout-shares-spec.md, «Calcolo»).
 * <p>
 * Expenses and fixed payments come first, then the association share, then the shares of the participants: the share
 * of the association is always a percentage of the whole base, and it is the first to give way when the margin does
 * not cover it. Fixed payments are not rounded; the shares are rounded down to the step, and what the rounding and the
 * factors leave goes to the association, as the residual. Expenses plus fixed payments plus the effective association
 * share plus the shares always make the base.
 */
public final class PayoutCalculator {

    public static final int SCALE = 4;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Without a step the shares keep two decimals: the cents are the smallest amount that can be paid. */
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final int PRECISION = 12;

    private PayoutCalculator() {
    }

    /** A participant of the event, with the factor or the fixed payment set for that event, if any. */
    public record Participant(Long userId, BigDecimal factor, BigDecimal fixedAmount) {
        public boolean fixed() {
            return fixedAmount != null;
        }
    }

    /**
     * What a participant receives: the fixed payment as it was set, or the share rounded down to the step.
     *
     * @param theoreticalShare the base share weighed by the factor, before the rounding; {@code null} for a fixed payment
     */
    public record Line(Long userId, BigDecimal factor, BigDecimal fixedAmount, BigDecimal theoreticalShare, BigDecimal amount) {
        public boolean fixed() {
            return fixedAmount != null;
        }
    }

    public record Distribution(
        BigDecimal base,
        BigDecimal expenses,
        BigDecimal fixedPayments,
        BigDecimal margin,
        BigDecimal fullAssociationShare,
        BigDecimal associationShare,
        BigDecimal net,
        BigDecimal baseShare,
        BigDecimal sharesTotal,
        BigDecimal residual,
        BigDecimal deficit,
        BigDecimal effectiveAssociationShare,
        int sharingParticipants,
        List<Line> lines
    ) {
        public boolean associationShareReduced() {
            return associationShare.compareTo(fullAssociationShare) < 0;
        }
    }

    /**
     * @param base         the fee of the event (planned) or its income (actual), never negative
     * @param expenses     the costs of the event (planned) or its expenses without the settlements (actual)
     * @param sharePercent the association share, between 0 and 100
     * @param roundingStep the step the shares are rounded down to; zero or {@code null} for the cents
     * @param participants the present users, each at most once
     */
    public static Distribution distribute(
        BigDecimal base,
        BigDecimal expenses,
        BigDecimal sharePercent,
        BigDecimal roundingStep,
        List<Participant> participants
    ) {
        BigDecimal effectiveBase = money(base);
        BigDecimal effectiveExpenses = money(expenses);
        BigDecimal percent = Objects.requireNonNullElse(sharePercent, BigDecimal.ZERO);
        if (percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("The association share must be between 0 and 100");
        }
        if (roundingStep != null && roundingStep.signum() < 0) {
            throw new IllegalArgumentException("The rounding step cannot be negative");
        }
        BigDecimal step = roundingStep == null || roundingStep.signum() == 0 ? CENT : roundingStep;
        for (Participant participant : participants) {
            BigDecimal factor = participant.factor();
            if (factor != null && (factor.signum() < 0 || factor.compareTo(ONE) > 0)) {
                throw new IllegalArgumentException("The factor must be between 0 and 1");
            }
            if (participant.fixed() && participant.fixedAmount().signum() <= 0) {
                throw new IllegalArgumentException("A fixed payment must be greater than zero");
            }
        }

        BigDecimal fixedPayments = participants.stream()
            .filter(Participant::fixed)
            .map(participant -> money(participant.fixedAmount()))
            .reduce(ZERO, BigDecimal::add);
        BigDecimal margin = effectiveBase.subtract(effectiveExpenses).subtract(fixedPayments);
        BigDecimal fullAssociationShare = effectiveBase.multiply(percent)
            .divide(HUNDRED, 2, RoundingMode.HALF_UP)
            .setScale(SCALE, RoundingMode.UNNECESSARY);
        BigDecimal associationShare = margin.signum() <= 0 ? ZERO : fullAssociationShare.min(margin);
        BigDecimal net = margin.signum() <= 0 ? ZERO : margin.subtract(associationShare);
        BigDecimal deficit = margin.signum() < 0 ? margin.negate() : ZERO;

        int sharing = (int) participants.stream().filter(participant -> !participant.fixed()).count();
        BigDecimal baseShare = sharing == 0 ? ZERO : net.divide(BigDecimal.valueOf(sharing), SCALE, RoundingMode.DOWN);
        List<Line> lines = participants.stream()
            .map(participant -> participant.fixed()
                ? new Line(participant.userId(), null, money(participant.fixedAmount()), null, money(participant.fixedAmount()))
                : share(participant, net, sharing, step))
            .toList();
        BigDecimal sharesTotal = lines.stream().filter(line -> !line.fixed()).map(Line::amount).reduce(ZERO, BigDecimal::add);
        BigDecimal residual = net.subtract(sharesTotal);
        BigDecimal effectiveAssociationShare = associationShare.add(residual).subtract(deficit);
        return new Distribution(
            effectiveBase,
            effectiveExpenses,
            fixedPayments,
            margin,
            fullAssociationShare,
            associationShare,
            net,
            baseShare,
            sharesTotal,
            residual,
            deficit,
            effectiveAssociationShare,
            sharing,
            lines
        );
    }

    private static Line share(Participant participant, BigDecimal net, int sharing, BigDecimal step) {
        BigDecimal factor = Objects.requireNonNullElse(participant.factor(), ONE);
        // The factor weighs the net before the division, so that the rounding happens once, on the share of the person.
        BigDecimal theoretical = net.multiply(factor).divide(BigDecimal.valueOf(sharing), PRECISION, RoundingMode.DOWN);
        BigDecimal amount = theoretical.divide(step, 0, RoundingMode.FLOOR).multiply(step).setScale(SCALE, RoundingMode.DOWN);
        return new Line(participant.userId(), participant.factor(), null, theoretical.setScale(SCALE, RoundingMode.DOWN), amount);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
