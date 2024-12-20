package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class PiecesTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Pieces getPiecesSample1() {
        return new Pieces()
            .id(1L)
            .insertBy("insertBy1")
            .editBy("editBy1")
            .name("name1")
            .description("description1")
            .contentType("contentType1")
            .path("path1")
            .orderNumber(1L);
    }

    public static Pieces getPiecesSample2() {
        return new Pieces()
            .id(2L)
            .insertBy("insertBy2")
            .editBy("editBy2")
            .name("name2")
            .description("description2")
            .contentType("contentType2")
            .path("path2")
            .orderNumber(2L);
    }

    public static Pieces getPiecesRandomSampleGenerator() {
        return new Pieces()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .contentType(UUID.randomUUID().toString())
            .path(UUID.randomUUID().toString())
            .orderNumber(longCount.incrementAndGet());
    }
}
