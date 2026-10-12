package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoanDurationPolicyTest {

    private static final Durations PARAMETERS = new Durations(12, 24, 24, 36);
    private static final LocalDate DELIVERY = LocalDate.of(2026, 9, 28);

    @Test
    void requiresTheLoanTypeAndTheExpirationOfInstruments() {
        assertThat(LoanDurationPolicy.checkDelivery(null, DELIVERY, null, PARAMETERS)).get()
            .extracting(LoanDurationPolicy.Violation::code).isEqualTo("missingLoanType");
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.FIXED_TERM, DELIVERY, null, PARAMETERS)).get()
            .extracting(LoanDurationPolicy.Violation::code).isEqualTo("missingExpiration");
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.PRECARIOUS, DELIVERY, DELIVERY.plusYears(1), PARAMETERS)).get()
            .extracting(LoanDurationPolicy.Violation::code).isEqualTo("precariousExpiration");
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.PRECARIOUS, DELIVERY, null, PARAMETERS)).isEmpty();
    }

    @Test
    void acceptsExpirationsWithinTheBoundsIncluded() {
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.FIXED_TERM, DELIVERY, DELIVERY.plusMonths(12), PARAMETERS)).isEmpty();
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.FIXED_TERM, DELIVERY, DELIVERY.plusMonths(24), PARAMETERS)).isEmpty();
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.FIXED_TERM, DELIVERY, DELIVERY.plusMonths(12).minusDays(1), PARAMETERS)).get()
            .extracting(LoanDurationPolicy.Violation::code).isEqualTo("expirationOutOfRange");
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.FIXED_TERM, DELIVERY, DELIVERY.plusMonths(24).plusDays(1), PARAMETERS)).get()
            .extracting(violation -> violation.args().values())
            .isEqualTo(Map.of(
                "minimum", "2027-09-28",
                "maximum", "2028-09-28",
                "minMonths", 12,
                "maxMonths", 24,
                "deliveredOn", "2026-09-28"
            ));
    }

    @Test
    void clampsMonthsAtTheEndOfFebruary() {
        Durations oneMonth = new Durations(1, 1, 1, 1);
        LocalDate lastOfJanuary = LocalDate.of(2027, 1, 31);

        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.FIXED_TERM, lastOfJanuary, LocalDate.of(2027, 2, 28), oneMonth)).isEmpty();
        assertThat(LoanDurationPolicy.checkDelivery(InventoryLoanType.FIXED_TERM, lastOfJanuary, LocalDate.of(2027, 3, 1), oneMonth)).isPresent();
    }

    @Test
    void acceptsARenewalBeforeTheExpirationWithinBothLimits() {
        LocalDate expiration = DELIVERY.plusMonths(24);

        assertThat(LoanDurationPolicy.checkRenewal(expiration, DELIVERY.plusMonths(36), expiration, expiration.minusDays(3), DELIVERY, PARAMETERS)).isEmpty();
    }

    @Test
    void rejectsRenewalsAfterTheExpirationOrWithAuthorizationAfterTheRenewal() {
        LocalDate expiration = DELIVERY.plusMonths(12);

        assertThat(LoanDurationPolicy.checkRenewal(expiration, expiration.plusMonths(6), expiration.plusDays(1), expiration, DELIVERY, PARAMETERS)).get()
            .extracting(LoanDurationPolicy.Violation::code).isEqualTo("afterExpiration");
        assertThat(LoanDurationPolicy.checkRenewal(expiration, expiration.plusMonths(6), expiration.minusDays(5), expiration.minusDays(1), DELIVERY, PARAMETERS)).get()
            .extracting(LoanDurationPolicy.Violation::code).isEqualTo("authorizationAfterRenewal");
        assertThat(LoanDurationPolicy.checkRenewal(expiration, expiration, expiration.minusDays(5), expiration.minusDays(6), DELIVERY, PARAMETERS)).get()
            .extracting(LoanDurationPolicy.Violation::code).isEqualTo("notExtended");
    }

    @Test
    void rejectsRenewalsBeyondTheRenewalOrTheTotalLimit() {
        LocalDate expiration = DELIVERY.plusMonths(12);
        LocalDate renewedOn = expiration.minusDays(10);

        assertThat(LoanDurationPolicy.checkRenewal(expiration, expiration.plusMonths(24).plusDays(1), renewedOn, renewedOn, DELIVERY, new Durations(12, 24, 24, 60)))
            .get().satisfies(violation -> {
                assertThat(violation.code()).isEqualTo("renewalLimitExceeded");
                assertThat(violation.args().values()).isEqualTo(Map.of("limit", "2029-09-28", "months", 24));
            });
        assertThat(LoanDurationPolicy.checkRenewal(expiration, DELIVERY.plusMonths(36).plusDays(1), renewedOn, renewedOn, DELIVERY, new Durations(12, 24, 48, 36)))
            .get().satisfies(violation -> {
                assertThat(violation.code()).isEqualTo("totalLimitExceeded");
                assertThat(violation.args().values()).isEqualTo(Map.of("limit", "2029-09-28", "months", 36, "firstDelivery", "2026-09-28"));
            });
    }
}
