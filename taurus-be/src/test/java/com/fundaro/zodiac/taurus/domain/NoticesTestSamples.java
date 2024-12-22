package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class NoticesTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Notices getNoticesSample1() {
        return new Notices().id(1L).insertBy("insertBy1").editBy("editBy1").userId("userId1").name("name1").message("message1");
    }

    public static Notices getNoticesSample2() {
        return new Notices().id(2L).insertBy("insertBy2").editBy("editBy2").userId("userId2").name("name2").message("message2");
    }

    public static Notices getNoticesRandomSampleGenerator() {
        return new Notices()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .userId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .message(UUID.randomUUID().toString());
    }
}
