package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class MediaTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Media getMediaSample1() {
        return new Media().id(1L).insertBy("insertBy1").editBy("editBy1").name("name1").description("description1").orderNumber(1L);
    }

    public static Media getMediaSample2() {
        return new Media().id(2L).insertBy("insertBy2").editBy("editBy2").name("name2").description("description2").orderNumber(2L);
    }

    public static Media getMediaRandomSampleGenerator() {
        return new Media()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .orderNumber(longCount.incrementAndGet());
    }
}
