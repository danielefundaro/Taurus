package com.fundaro.zodiac.taurus.service.finance;

import com.fundaro.zodiac.taurus.service.finance.PayoutCalculator.Distribution;
import com.fundaro.zodiac.taurus.service.finance.PayoutCalculator.Line;
import com.fundaro.zodiac.taurus.service.finance.PayoutCalculator.Participant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The formula and the examples of docs/event-payout-shares-spec.md, «Calcolo». */
class PayoutCalculatorTest {

    @Test
    void reproducesTheExampleWithAFactor() {
        List<Participant> participants = new ArrayList<>(plain(1, 4));
        participants.add(new Participant(5L, new BigDecimal("0.5"), null));

        Distribution result = PayoutCalculator.distribute(money("1000"), money("120"), new BigDecimal("20"), money("5"), participants);

        assertThat(result.fullAssociationShare()).isEqualByComparingTo("200");
        assertThat(result.associationShare()).isEqualByComparingTo("200");
        assertThat(result.net()).isEqualByComparingTo("680");
        assertThat(result.baseShare()).isEqualByComparingTo("136");
        assertThat(amounts(result)).containsExactly(money("135"), money("135"), money("135"), money("135"), money("65"));
        assertThat(result.lines().get(4).theoreticalShare()).isEqualByComparingTo("68");
        assertThat(result.sharesTotal()).isEqualByComparingTo("605");
        assertThat(result.residual()).isEqualByComparingTo("75");
        assertThat(result.deficit()).isEqualByComparingTo("0");
        assertThat(result.effectiveAssociationShare()).isEqualByComparingTo("275");
        assertBalanced(result);
    }

    @Test
    void reproducesTheExampleWithAFixedPayment() {
        List<Participant> participants = new ArrayList<>(plain(1, 4));
        participants.add(new Participant(5L, null, money("180")));

        Distribution result = PayoutCalculator.distribute(money("1000"), money("120"), new BigDecimal("20"), money("5"), participants);

        assertThat(result.fixedPayments()).isEqualByComparingTo("180");
        assertThat(result.margin()).isEqualByComparingTo("700");
        assertThat(result.associationShare()).isEqualByComparingTo("200");
        assertThat(result.associationShareReduced()).isFalse();
        assertThat(result.net()).isEqualByComparingTo("500");
        assertThat(result.sharingParticipants()).isEqualTo(4);
        assertThat(result.baseShare()).isEqualByComparingTo("125");
        assertThat(amounts(result)).containsExactly(money("125"), money("125"), money("125"), money("125"), money("180"));
        assertThat(result.lines().get(4).fixed()).isTrue();
        assertThat(result.lines().get(4).theoreticalShare()).isNull();
        assertThat(result.residual()).isEqualByComparingTo("0");
        assertThat(result.effectiveAssociationShare()).isEqualByComparingTo("200");
        assertBalanced(result);
    }

    @Test
    void reducesTheAssociationShareToTheMargin() {
        List<Participant> participants = new ArrayList<>(plain(1, 3));
        participants.add(new Participant(4L, null, money("300")));

        Distribution result = PayoutCalculator.distribute(money("500"), money("150"), new BigDecimal("20"), money("5"), participants);

        assertThat(result.margin()).isEqualByComparingTo("50");
        assertThat(result.fullAssociationShare()).isEqualByComparingTo("100");
        assertThat(result.associationShare()).isEqualByComparingTo("50");
        assertThat(result.associationShareReduced()).isTrue();
        assertThat(result.net()).isEqualByComparingTo("0");
        assertThat(amounts(result)).containsExactly(money("0"), money("0"), money("0"), money("300"));
        assertThat(result.effectiveAssociationShare()).isEqualByComparingTo("50");
        assertBalanced(result);
    }

    @Test
    void reducesTheAssociationShareAlsoWhenOnlyTheExpensesEatIt() {
        Distribution result = PayoutCalculator.distribute(money("100"), money("90"), new BigDecimal("20"), money("5"), plain(1, 2));

        assertThat(result.associationShare()).isEqualByComparingTo("10");
        assertThat(result.net()).isEqualByComparingTo("0");
        assertThat(result.deficit()).isEqualByComparingTo("0");
        assertBalanced(result);
    }

    @Test
    void paysTheFixedPaymentOfAnEventWithoutFeeOutOfTheAssociation() {
        List<Participant> participants = new ArrayList<>(plain(1, 3));
        participants.add(new Participant(4L, null, money("100")));

        Distribution result = PayoutCalculator.distribute(null, money("40"), new BigDecimal("20"), money("5"), participants);

        assertThat(result.margin()).isEqualByComparingTo("-140");
        assertThat(result.associationShare()).isEqualByComparingTo("0");
        assertThat(result.net()).isEqualByComparingTo("0");
        assertThat(amounts(result)).containsExactly(money("0"), money("0"), money("0"), money("100"));
        assertThat(result.deficit()).isEqualByComparingTo("140");
        assertThat(result.effectiveAssociationShare()).isEqualByComparingTo("-140");
        assertBalanced(result);
    }

    @Test
    void refusesAFactorOutsideZeroAndOne() {
        assertThatThrownBy(() -> PayoutCalculator.distribute(money("100"), money("0"), BigDecimal.TEN, money("5"),
            List.of(new Participant(1L, new BigDecimal("1.001"), null))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayoutCalculator.distribute(money("100"), money("0"), BigDecimal.TEN, money("5"),
            List.of(new Participant(1L, new BigDecimal("-0.1"), null))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayoutCalculator.distribute(money("100"), money("0"), BigDecimal.TEN, money("5"),
            List.of(new Participant(1L, null, BigDecimal.ZERO))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayoutCalculator.distribute(money("100"), money("0"), new BigDecimal("101"), money("5"), plain(1, 1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayoutCalculator.distribute(money("100"), money("0"), BigDecimal.TEN, money("-1"), plain(1, 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reducingAFactorLeavesTheOtherSharesAndRaisesTheResidual() {
        Distribution full = PayoutCalculator.distribute(money("1000"), money("0"), BigDecimal.ZERO, money("5"), plain(1, 4));
        List<Participant> reduced = new ArrayList<>(plain(1, 3));
        reduced.add(new Participant(4L, new BigDecimal("0.25"), null));
        Distribution partial = PayoutCalculator.distribute(money("1000"), money("0"), BigDecimal.ZERO, money("5"), reduced);

        assertThat(amounts(full)).containsExactly(money("250"), money("250"), money("250"), money("250"));
        assertThat(amounts(partial)).containsExactly(money("250"), money("250"), money("250"), money("60"));
        assertThat(partial.residual()).isGreaterThan(full.residual());
        assertThat(partial.residual()).isEqualByComparingTo("190");
    }

    @Test
    void roundsDownToTheStepAndLeavesTheRestToTheAssociation() {
        Distribution result = PayoutCalculator.distribute(money("100"), money("0"), BigDecimal.ZERO, money("10"), plain(1, 3));

        assertThat(amounts(result)).containsExactly(money("30"), money("30"), money("30"));
        assertThat(result.residual()).isEqualByComparingTo("10");
    }

    @Test
    void givesNothingWhenTheTheoreticalShareIsBelowTheStep() {
        Distribution result = PayoutCalculator.distribute(money("12"), money("0"), BigDecimal.ZERO, money("5"), plain(1, 3));

        assertThat(amounts(result)).containsExactly(money("0"), money("0"), money("0"));
        assertThat(result.residual()).isEqualByComparingTo("12");
    }

    @Test
    void keepsTheCentsWithoutAStep() {
        Distribution result = PayoutCalculator.distribute(money("100"), money("0"), BigDecimal.ZERO, BigDecimal.ZERO, plain(1, 3));

        assertThat(amounts(result)).containsExactly(money("33.33"), money("33.33"), money("33.33"));
        assertThat(result.residual()).isEqualByComparingTo("0.01");
        assertThat(PayoutCalculator.distribute(money("100"), money("0"), BigDecimal.ZERO, null, plain(1, 3)).residual())
            .isEqualByComparingTo("0.01");
    }

    @Test
    void doesNotRoundAFixedPaymentToTheStep() {
        List<Participant> participants = new ArrayList<>(plain(1, 1));
        participants.add(new Participant(2L, null, money("33.33")));

        Distribution result = PayoutCalculator.distribute(money("200"), money("0"), BigDecimal.ZERO, money("5"), participants);

        assertThat(result.lines().get(1).amount()).isEqualByComparingTo("33.33");
        assertThat(result.lines().get(0).amount()).isEqualByComparingTo("165");
        assertBalanced(result);
    }

    @Test
    void handlesTheEventsWithoutSharesToDistribute() {
        Distribution nobody = PayoutCalculator.distribute(money("500"), money("100"), new BigDecimal("10"), money("5"), List.of());
        assertThat(nobody.lines()).isEmpty();
        assertThat(nobody.residual()).isEqualByComparingTo("350");
        assertBalanced(nobody);

        Distribution zeroFactors = PayoutCalculator.distribute(money("500"), money("100"), new BigDecimal("10"), money("5"), List.of(
            new Participant(1L, BigDecimal.ZERO, null),
            new Participant(2L, BigDecimal.ZERO, null)
        ));
        assertThat(amounts(zeroFactors)).containsExactly(money("0"), money("0"));
        assertThat(zeroFactors.residual()).isEqualByComparingTo("350");
        assertBalanced(zeroFactors);

        Distribution allFixed = PayoutCalculator.distribute(money("500"), money("100"), new BigDecimal("10"), money("5"), List.of(
            new Participant(1L, null, money("50")),
            new Participant(2L, null, money("50"))
        ));
        assertThat(allFixed.sharingParticipants()).isZero();
        assertThat(allFixed.net()).isEqualByComparingTo("250");
        assertThat(allFixed.residual()).isEqualByComparingTo("250");
        assertThat(allFixed.effectiveAssociationShare()).isEqualByComparingTo("300");
        assertBalanced(allFixed);

        Distribution negative = PayoutCalculator.distribute(money("50"), money("80"), new BigDecimal("10"), money("5"), plain(1, 2));
        assertThat(negative.net()).isEqualByComparingTo("0");
        assertThat(negative.associationShare()).isEqualByComparingTo("0");
        assertThat(negative.deficit()).isEqualByComparingTo("30");
        assertBalanced(negative);
    }

    @Test
    void alwaysBalancesAndNeverDistributesMoreThanTheNet() {
        Random random = new Random(20261113L);
        for (int run = 0; run < 2000; run++) {
            List<Participant> participants = new ArrayList<>();
            int count = random.nextInt(8);
            for (int index = 0; index < count; index++) {
                int kind = random.nextInt(3);
                participants.add(new Participant(
                    (long) index,
                    kind == 1 ? BigDecimal.valueOf(random.nextInt(1001), 3) : null,
                    kind == 2 ? BigDecimal.valueOf(1 + random.nextInt(50000), 2) : null
                ));
            }
            Distribution result = PayoutCalculator.distribute(
                BigDecimal.valueOf(random.nextInt(300000), 2),
                BigDecimal.valueOf(random.nextInt(100000), 2),
                BigDecimal.valueOf(random.nextInt(10001), 2),
                BigDecimal.valueOf(new int[] {0, 100, 200, 500, 1000, 2000}[random.nextInt(6)], 2),
                participants
            );
            assertBalanced(result);
            assertThat(result.residual().signum()).isGreaterThanOrEqualTo(0);
            assertThat(result.net().signum()).isGreaterThanOrEqualTo(0);
            assertThat(result.sharesTotal()).isLessThanOrEqualTo(result.net());
            assertThat(result.associationShare()).isLessThanOrEqualTo(result.fullAssociationShare());
        }
    }

    private static void assertBalanced(Distribution result) {
        BigDecimal total = result.expenses().add(result.fixedPayments()).add(result.effectiveAssociationShare()).add(result.sharesTotal());
        assertThat(total).isEqualByComparingTo(result.base());
    }

    private static List<BigDecimal> amounts(Distribution result) {
        return result.lines().stream().map(Line::amount).toList();
    }

    private static List<Participant> plain(long first, int count) {
        return LongStream.range(first, first + count).mapToObj(id -> new Participant(id, null, null)).toList();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(4);
    }
}
