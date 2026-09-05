package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class CalendarFeedTokenServiceTest {
    private final CalendarFeedTokenService service = new CalendarFeedTokenService();
    @Test void createsOpaqueUrlSafeTokensAndDigests() {
        var values = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            var token = service.generate();
            assertThat(token.value()).matches("[A-Za-z0-9_-]{43}");
            assertThat(token.digest()).hasSize(32);
            assertThat(service.decodeAndDigest(token.value())).isEqualTo(token.digest());
            values.add(token.value());
        }
        assertThat(values).hasSize(100);
        assertThat(service.decodeAndDigest("invalid")).isNull();
    }
}
