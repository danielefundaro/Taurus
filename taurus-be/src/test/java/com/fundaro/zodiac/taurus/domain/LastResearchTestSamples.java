package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class LastResearchTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static LastResearch getLastResearchSample1() {
        return new LastResearch().id(1L).insertBy("insertBy1").editBy("editBy1").userId("userId1").value("value1").field("field1");
    }

    public static LastResearch getLastResearchSample2() {
        return new LastResearch().id(2L).insertBy("insertBy2").editBy("editBy2").userId("userId2").value("value2").field("field2");
    }

    public static LastResearch getLastResearchRandomSampleGenerator() {
        return new LastResearch()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .userId(UUID.randomUUID().toString())
            .value(UUID.randomUUID().toString())
            .field(UUID.randomUUID().toString());
    }
}
