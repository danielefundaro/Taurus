package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;
import com.fundaro.zodiac.taurus.web.rest.errors.ErrorArgs;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Duration rules of the loan (art. 4 of the default template), with the durations registered on the sheet or, before the
 * issue, those of the tenant. A violation carries a code and its parameters: the frontend writes the message.
 */
public final class LoanDurationPolicy {

    private LoanDurationPolicy() {
    }

    /**
     * Codes of a delivery that breaks the rules: they complete the keys {@code inventory.loanDuration.<code>} and
     * {@code inventory.loanSheet.<code>} of the error catalog.
     */
    public enum DeliveryCode {
        MISSING_LOAN_TYPE("missingLoanType"),
        PRECARIOUS_EXPIRATION("precariousExpiration"),
        MISSING_EXPIRATION("missingExpiration"),
        EXPIRATION_OUT_OF_RANGE("expirationOutOfRange");

        private final String key;

        DeliveryCode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * Codes of a renewal that breaks the rules: they complete the keys {@code inventory.renewal.<code>}.
     */
    public enum RenewalCode {
        MISSING_EXPIRATION("missingExpiration"),
        AFTER_EXPIRATION("afterExpiration"),
        AUTHORIZATION_AFTER_RENEWAL("authorizationAfterRenewal"),
        NOT_EXTENDED("notExtended"),
        RENEWAL_LIMIT_EXCEEDED("renewalLimitExceeded"),
        TOTAL_LIMIT_EXCEEDED("totalLimitExceeded");

        private final String key;

        RenewalCode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public record Violation(String code, ErrorArgs args) {
        static Violation of(DeliveryCode code) {
            return new Violation(code.key(), new ErrorArgs());
        }

        static Violation of(RenewalCode code) {
            return new Violation(code.key(), new ErrorArgs());
        }

        Violation with(String name, Object value) {
            args.with(name, value);
            return this;
        }
    }

    /**
     * Checks the regime and the expiration date of a delivery, either at issue time or as a later delivery.
     */
    public static Optional<Violation> checkDelivery(InventoryLoanType loanType, LocalDate deliveredOn, LocalDate expirationDate, Durations parameters) {
        if (loanType == null) {
            return Optional.of(Violation.of(DeliveryCode.MISSING_LOAN_TYPE));
        }
        if (loanType == InventoryLoanType.PRECARIOUS) {
            return expirationDate == null ? Optional.empty() : Optional.of(Violation.of(DeliveryCode.PRECARIOUS_EXPIRATION));
        }
        if (expirationDate == null) {
            return Optional.of(Violation.of(DeliveryCode.MISSING_EXPIRATION));
        }
        LocalDate minimum = deliveredOn.plusMonths(parameters.minMonths());
        LocalDate maximum = deliveredOn.plusMonths(parameters.maxMonths());
        if (expirationDate.isBefore(minimum) || expirationDate.isAfter(maximum)) {
            return Optional.of(
                Violation.of(DeliveryCode.EXPIRATION_OUT_OF_RANGE)
                    .with("minimum", minimum)
                    .with("maximum", maximum)
                    .with("minMonths", parameters.minMonths())
                    .with("maxMonths", parameters.maxMonths())
                    .with("deliveredOn", deliveredOn)
            );
        }
        return Optional.empty();
    }

    /**
     * Checks a renewal: before the current expiration, within the renewal limit and within the total limit counted from
     * the first delivery of the same instrument.
     */
    public static Optional<Violation> checkRenewal(
        LocalDate currentExpiration,
        LocalDate newExpiration,
        LocalDate renewedOn,
        LocalDate authorizationDate,
        LocalDate firstDelivery,
        Durations parameters
    ) {
        if (currentExpiration == null) {
            return Optional.of(Violation.of(RenewalCode.MISSING_EXPIRATION));
        }
        if (renewedOn.isAfter(currentExpiration)) {
            return Optional.of(Violation.of(RenewalCode.AFTER_EXPIRATION).with("currentExpiration", currentExpiration));
        }
        if (authorizationDate.isAfter(renewedOn)) {
            return Optional.of(Violation.of(RenewalCode.AUTHORIZATION_AFTER_RENEWAL));
        }
        if (!newExpiration.isAfter(currentExpiration)) {
            return Optional.of(Violation.of(RenewalCode.NOT_EXTENDED));
        }
        LocalDate renewalLimit = currentExpiration.plusMonths(parameters.renewalMaxMonths());
        if (newExpiration.isAfter(renewalLimit)) {
            return Optional.of(
                Violation.of(RenewalCode.RENEWAL_LIMIT_EXCEEDED).with("limit", renewalLimit).with("months", parameters.renewalMaxMonths())
            );
        }
        LocalDate totalLimit = firstDelivery.plusMonths(parameters.totalMaxMonths());
        if (newExpiration.isAfter(totalLimit)) {
            return Optional.of(
                Violation.of(RenewalCode.TOTAL_LIMIT_EXCEEDED)
                    .with("limit", totalLimit)
                    .with("months", parameters.totalMaxMonths())
                    .with("firstDelivery", firstDelivery)
            );
        }
        return Optional.empty();
    }
}
