package com.fundaro.zodiac.taurus.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class TracksTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Tracks getTracksSample1() {
        return new Tracks()
            .id(1L)
            .insertBy("insertBy1")
            .editBy("editBy1")
            .name("name1")
            .description("description1")
            .composer("composer1")
            .arranger("arranger1");
    }

    public static Tracks getTracksSample2() {
        return new Tracks()
            .id(2L)
            .insertBy("insertBy2")
            .editBy("editBy2")
            .name("name2")
            .description("description2")
            .composer("composer2")
            .arranger("arranger2");
    }

    public static Tracks getTracksRandomSampleGenerator() {
        return new Tracks()
            .id(longCount.incrementAndGet())
            .insertBy(UUID.randomUUID().toString())
            .editBy(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .composer(UUID.randomUUID().toString())
            .arranger(UUID.randomUUID().toString());
    }
}
