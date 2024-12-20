package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class CollectionsTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Collections getCollectionsSample1() {
        return new Collections().id(1L).insertBy("insertBy1").editBy("editBy1").orderNumber(1L);
    }

    public static Collections getCollectionsSample2() {
        return new Collections().id(2L).insertBy("insertBy2").editBy("editBy2").orderNumber(2L);
    }

    public static Collections getCollectionsRandomSampleGenerator() {
        return new Collections()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .orderNumber(longCount.incrementAndGet());
    }
}
