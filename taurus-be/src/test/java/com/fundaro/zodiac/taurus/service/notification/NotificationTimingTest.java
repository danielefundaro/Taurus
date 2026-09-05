package com.fundaro.zodiac.taurus.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class NotificationTimingTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Test
    void postponesAcrossMidnightToTheQuietHoursEnd() {
        ZonedDateTime requested = ZonedDateTime.of(2026, 9, 5, 23, 30, 0, 0, ROME);

        ZonedDateTime result = NotificationTiming.nextAllowed(preference(null), requested);

        assertThat(result).isEqualTo(ZonedDateTime.of(2026, 9, 6, 7, 0, 0, 0, ROME));
    }

    @Test
    void postponesAnEarlyMorningInstantToTheSameDayQuietHoursEnd() {
        ZonedDateTime requested = ZonedDateTime.of(2026, 9, 6, 3, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextAllowed(preference(null), requested))
            .isEqualTo(ZonedDateTime.of(2026, 9, 6, 7, 0, 0, 0, ROME));
    }

    @Test
    void resolvesASameDayQuietIntervalWithoutCrossingMidnight() {
        var preference = new NotificationPreferenceDecision(
            "user-1", true, NotificationPushMode.IMMEDIATE, true, ROME, LocalTime.of(20, 0), true,
            LocalTime.of(13, 0), LocalTime.of(15, 0), null, NotificationPushPreview.PRIVATE, false
        );

        assertThat(NotificationTiming.nextAllowed(preference, ZonedDateTime.of(2026, 9, 5, 14, 0, 0, 0, ROME)))
            .isEqualTo(ZonedDateTime.of(2026, 9, 5, 15, 0, 0, 0, ROME));
    }

    @Test
    void treatsTheQuietIntervalAsHalfOpen() {
        // [start, end): l'inizio è silenzioso, la fine no.
        assertThat(NotificationTiming.isWithin(LocalTime.of(22, 0), LocalTime.of(7, 0), LocalTime.of(22, 0))).isTrue();
        assertThat(NotificationTiming.isWithin(LocalTime.of(22, 0), LocalTime.of(7, 0), LocalTime.of(7, 0))).isFalse();
        assertThat(NotificationTiming.isWithin(LocalTime.of(13, 0), LocalTime.of(15, 0), LocalTime.of(13, 0))).isTrue();
        assertThat(NotificationTiming.isWithin(LocalTime.of(13, 0), LocalTime.of(15, 0), LocalTime.of(15, 0))).isFalse();
    }

    @Test
    void leavesAnInstantOutsideTheQuietIntervalUntouched() {
        ZonedDateTime requested = ZonedDateTime.of(2026, 9, 5, 12, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextAllowed(preference(null), requested)).isEqualTo(requested);
    }

    @Test
    void appliesPauseBeforeRecheckingQuietHours() {
        ZonedDateTime requested = ZonedDateTime.of(2026, 9, 5, 12, 0, 0, 0, ROME);
        ZonedDateTime pause = ZonedDateTime.of(2026, 9, 5, 23, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextAllowed(preference(pause), requested))
            .isEqualTo(ZonedDateTime.of(2026, 9, 6, 7, 0, 0, 0, ROME));
    }

    @Test
    void ignoresAPauseThatHasAlreadyExpired() {
        ZonedDateTime requested = ZonedDateTime.of(2026, 9, 5, 12, 0, 0, 0, ROME);
        ZonedDateTime pause = ZonedDateTime.of(2026, 9, 5, 9, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextAllowed(preference(pause), requested)).isEqualTo(requested);
    }

    @Test
    void endsAPauseOutsideQuietHoursWithoutFurtherDelay() {
        ZonedDateTime requested = ZonedDateTime.of(2026, 9, 5, 8, 0, 0, 0, ROME);
        ZonedDateTime pause = ZonedDateTime.of(2026, 9, 5, 18, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextAllowed(preference(pause), requested)).isEqualTo(pause);
    }

    @Test
    void resolvesANonexistentDstTimeToTheFirstValidInstant() {
        var preference = digestAt(LocalTime.of(2, 30));
        ZonedDateTime beforeChange = ZonedDateTime.of(2026, 3, 28, 8, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextDigest(preference, beforeChange).toLocalTime()).isEqualTo(LocalTime.of(3, 30));
    }

    @Test
    void resolvesAnAmbiguousDstTimeWithTheEarlierOffset() {
        var preference = digestAt(LocalTime.of(2, 30));
        // 25 ottobre 2026: l'ora legale termina e 02:30 esiste due volte.
        ZonedDateTime beforeChange = ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, ROME);

        ZonedDateTime digest = NotificationTiming.nextDigest(preference, beforeChange);

        assertThat(digest.toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 25));
        assertThat(digest.toLocalTime()).isEqualTo(LocalTime.of(2, 30));
        assertThat(digest.getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(2));
    }

    @Test
    void bucketsTheDigestOnTheSameLocalDayWhenTheTimeHasNotPassed() {
        var preference = digestAt(LocalTime.of(8, 0));
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 5, 6, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextDigest(preference, now))
            .isEqualTo(ZonedDateTime.of(2026, 9, 5, 8, 0, 0, 0, ROME));
    }

    @Test
    void bucketsTheDigestOnTheNextLocalDayOnceTheTimeHasPassed() {
        var preference = digestAt(LocalTime.of(8, 0));
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 5, 8, 0, 0, 0, ROME);

        assertThat(NotificationTiming.nextDigest(preference, now))
            .isEqualTo(ZonedDateTime.of(2026, 9, 6, 8, 0, 0, 0, ROME));
    }

    @Test
    void computesTheDigestBucketInThePersonalTimeZoneNotTheServerOne() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        var preference = new NotificationPreferenceDecision(
            "user-1", true, NotificationPushMode.DAILY_DIGEST, true, tokyo, LocalTime.of(8, 0), false,
            LocalTime.of(22, 0), LocalTime.of(7, 0), null, NotificationPushPreview.PRIVATE, false
        );
        // 22:00 del 5 settembre a Roma sono già le 05:00 del 6 a Tokyo: il bucket è il 6.
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 5, 22, 0, 0, 0, ROME);

        ZonedDateTime digest = NotificationTiming.nextDigest(preference, now);

        assertThat(digest.withZoneSameInstant(tokyo).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 6));
        assertThat(digest.withZoneSameInstant(tokyo).toLocalTime()).isEqualTo(LocalTime.of(8, 0));
    }

    private static NotificationPreferenceDecision digestAt(LocalTime digest) {
        return new NotificationPreferenceDecision(
            "user-1", true, NotificationPushMode.DAILY_DIGEST, true, ROME, digest, false,
            LocalTime.of(22, 0), LocalTime.of(7, 0), null, NotificationPushPreview.PRIVATE, false
        );
    }

    private static NotificationPreferenceDecision preference(ZonedDateTime pause) {
        return new NotificationPreferenceDecision(
            "user-1", true, NotificationPushMode.IMMEDIATE, true, ROME, LocalTime.of(8, 0), true,
            LocalTime.of(22, 0), LocalTime.of(7, 0), pause, NotificationPushPreview.PRIVATE, false
        );
    }
}
