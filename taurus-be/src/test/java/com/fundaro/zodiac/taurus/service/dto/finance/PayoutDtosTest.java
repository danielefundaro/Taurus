package com.fundaro.zodiac.taurus.service.dto.finance;

import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.AdjustmentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Digits;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixed payment keeps the size of the other amounts, fifteen integer digits and two decimals, so that it always
 * fits its column and an oversized one is refused as invalid data before reaching the database.
 */
class PayoutDtosTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAFixedPaymentWithFifteenIntegerDigitsAndTwoDecimals() {
        assertThat(validator.validate(fixed("999999999999999.99"))).isEmpty();
        assertThat(validator.validate(fixed("180"))).isEmpty();
    }

    @Test
    void refusesAFixedPaymentWithSixteenIntegerDigits() {
        assertDigitsViolation(validator.validate(fixed("1000000000000000")));
    }

    @Test
    void refusesAFixedPaymentWithMoreThanTwoDecimals() {
        assertDigitsViolation(validator.validate(fixed("180.005")));
    }

    @Test
    void leavesPresenceAndRangeToTheService() {
        assertThat(validator.validate(new AdjustmentRequest(null, null, null))).isEmpty();
        assertThat(validator.validate(new AdjustmentRequest(new BigDecimal("0.5"), new BigDecimal("-1"), " "))).isEmpty();
    }

    private static AdjustmentRequest fixed(String amount) {
        return new AdjustmentRequest(null, new BigDecimal(amount), "Ingaggio");
    }

    private static void assertDigitsViolation(Set<ConstraintViolation<AdjustmentRequest>> violations) {
        assertThat(violations)
            .singleElement()
            .satisfies(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("fixedAmount");
                assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(Digits.class);
            });
    }
}
