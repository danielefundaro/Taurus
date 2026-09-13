package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class CalendarFeedIdempotencyCodecTest {
    private final CalendarFeedIdempotencyCodec codec = new CalendarFeedIdempotencyCodec();

    @Test
    void encryptsAReplayableTokenWithoutPersistingPlaintext() {
        UUID key = UUID.randomUUID();
        byte[] digest = codec.digest("actor", "CREATE_PERSONAL", null, key);

        CalendarFeedIdempotencyCodec.EncryptedToken encrypted = codec.encrypt("calendar-secret", key, digest);

        assertThat(encrypted.ciphertext()).isNotEqualTo("calendar-secret".getBytes());
        assertThat(codec.decrypt(encrypted.ciphertext(), encrypted.nonce(), key, digest)).isEqualTo("calendar-secret");
    }

    @Test
    void bindsCiphertextToTheIdempotencyKeyAndRequest() {
        UUID key = UUID.randomUUID();
        byte[] digest = codec.digest("actor", "ROTATE", UUID.randomUUID(), key);
        CalendarFeedIdempotencyCodec.EncryptedToken encrypted = codec.encrypt("calendar-secret", key, digest);

        assertThatThrownBy(() -> codec.decrypt(encrypted.ciphertext(), encrypted.nonce(), UUID.randomUUID(), digest))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> codec.decrypt(encrypted.ciphertext(), encrypted.nonce(), key, new byte[32]))
            .isInstanceOf(IllegalStateException.class);
    }
}
