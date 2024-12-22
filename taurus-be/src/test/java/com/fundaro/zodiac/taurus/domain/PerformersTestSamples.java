package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PerformersTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Performers getPerformersSample1() {
        return new Performers().id(1L).insertBy("insertBy1").editBy("editBy1").description("description1");
    }

    public static Performers getPerformersSample2() {
        return new Performers().id(2L).insertBy("insertBy2").editBy("editBy2").description("description2");
    }

    public static Performers getPerformersRandomSampleGenerator() {
        return new Performers()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString());
    }
}
