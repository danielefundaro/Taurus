package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureCodesTest {

    private static final SignatureCodes.Binding BINDING = new SignatureCodes.Binding(7, "user-1", "ASSIGNMENT_DECISION", "a".repeat(64));

    @Test
    void generatesSixDigitCodes() {
        SignatureCodes codes = codes("secret");
        for (int i = 0; i < 200; i++) {
            assertThat(codes.generate()).matches("\\d{6}");
        }
    }

    @Test
    void storesAKeyedDigestBoundToTheChallengeTheSignerThePurposeAndTheItems() {
        SignatureCodes codes = codes("secret");
        String digest = codes.digest(BINDING, "123456");

        assertThat(digest).hasSize(64).doesNotContain("123456");
        assertThat(codes.matches(BINDING, "123456", digest)).isTrue();
        assertThat(codes.matches(BINDING, "123457", digest)).isFalse();
        assertThat(codes.matches(new SignatureCodes.Binding(8, "user-1", "ASSIGNMENT_DECISION", "a".repeat(64)), "123456", digest)).isFalse();
        assertThat(codes.matches(new SignatureCodes.Binding(7, "user-2", "ASSIGNMENT_DECISION", "a".repeat(64)), "123456", digest)).isFalse();
        assertThat(codes.matches(new SignatureCodes.Binding(7, "user-1", "ACCEPTANCE", "a".repeat(64)), "123456", digest)).isFalse();
        assertThat(codes.matches(new SignatureCodes.Binding(7, "user-1", "ASSIGNMENT_DECISION", "b".repeat(64)), "123456", digest)).isFalse();
        assertThat(codes("other-secret").matches(BINDING, "123456", digest)).isFalse();
    }

    @Test
    void refusesMalformedCodesAndMissingDigests() {
        SignatureCodes codes = codes("secret");
        String digest = codes.digest(BINDING, "123456");

        assertThat(codes.matches(BINDING, "12345", digest)).isFalse();
        assertThat(codes.matches(BINDING, "12345a", digest)).isFalse();
        assertThat(codes.matches(BINDING, null, digest)).isFalse();
        assertThat(codes.matches(BINDING, "123456", null)).isFalse();
    }

    @Test
    void cannotCreateCodesWithoutTheSecret() {
        SignatureCodes codes = codes(" ");

        assertThat(codes.configured()).isFalse();
        assertThatThrownBy(() -> codes.digest(BINDING, "123456")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theConfirmationRequestDoesNotPrintTheCode() {
        assertThat(new SignatureDtos.ConfirmRequest("123456").toString()).doesNotContain("123456");
    }

    static SignatureCodes codes(String secret) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().setOtpSecret(secret);
        return new SignatureCodes(properties);
    }
}
