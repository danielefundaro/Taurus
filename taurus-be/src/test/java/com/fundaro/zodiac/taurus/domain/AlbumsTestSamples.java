package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class AlbumsTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Albums getAlbumsSample1() {
        return new Albums().id(1L).insertBy("insertBy1").editBy("editBy1").name("name1").description("description1");
    }

    public static Albums getAlbumsSample2() {
        return new Albums().id(2L).insertBy("insertBy2").editBy("editBy2").name("name2").description("description2");
    }

    public static Albums getAlbumsRandomSampleGenerator() {
        return new Albums()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString());
    }
}
