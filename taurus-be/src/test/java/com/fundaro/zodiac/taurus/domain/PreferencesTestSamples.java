package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PreferencesTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Preferences getPreferencesSample1() {
        return new Preferences().id(1L).insertBy("insertBy1").editBy("editBy1").userId("userId1").key("key1").value("value1");
    }

    public static Preferences getPreferencesSample2() {
        return new Preferences().id(2L).insertBy("insertBy2").editBy("editBy2").userId("userId2").key("key2").value("value2");
    }

    public static Preferences getPreferencesRandomSampleGenerator() {
        return new Preferences()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .userId(UUID.randomUUID().toString())
            .key(UUID.randomUUID().toString())
            .value(UUID.randomUUID().toString());
    }
}
