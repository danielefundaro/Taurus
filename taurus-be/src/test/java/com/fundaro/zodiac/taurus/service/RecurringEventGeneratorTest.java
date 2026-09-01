package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceFrequency;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceWeekDay;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceEndDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceRuleDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurringEventGeneratorTest {

    private final RecurringEventGenerator generator = new RecurringEventGenerator();
    private final ZoneId rome = ZoneId.of("Europe/Rome");

    @Test
    void generatesDailyOccurrencesByCount() {
        List<LocalDate> dates = generator.generate(
            LocalDateTime.of(2026, 9, 1, 20, 0),
            rome,
            countRule(RecurrenceFrequency.DAILY, 2, 3),
            500
        ).stream().map(value -> value.toLocalDate()).toList();

        assertThat(dates).containsExactly(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 3),
            LocalDate.of(2026, 9, 5)
        );
    }

    @Test
    void generatesMultipleWeekdaysAtTheSameLocalTime() {
        RecurrenceRuleDTO rule = countRule(RecurrenceFrequency.WEEKLY, 1, 5);
        rule.setWeekDays(List.of(RecurrenceWeekDay.MO, RecurrenceWeekDay.TH));

        var occurrences = generator.generate(LocalDateTime.of(2026, 9, 7, 20, 0), rome, rule, 500);

        assertThat(occurrences).extracting(value -> value.toLocalDate()).containsExactly(
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 9, 10),
            LocalDate.of(2026, 9, 14),
            LocalDate.of(2026, 9, 17),
            LocalDate.of(2026, 9, 21)
        );
        assertThat(occurrences).allMatch(value -> value.toLocalTime().equals(LocalTime.of(20, 0)));
    }

    @Test
    void skipsMonthsWithoutTheRequestedDay() {
        var occurrences = generator.generate(
            LocalDateTime.of(2026, 1, 31, 18, 0),
            rome,
            countRule(RecurrenceFrequency.MONTHLY, 1, 4),
            500
        );

        assertThat(occurrences).extracting(value -> value.toLocalDate()).containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 5, 31),
            LocalDate.of(2026, 7, 31)
        );
    }

    @Test
    void skipsNonLeapYearsForFebruaryTwentyNinth() {
        var occurrences = generator.generate(
            LocalDateTime.of(2024, 2, 29, 10, 0),
            rome,
            countRule(RecurrenceFrequency.YEARLY, 1, 3),
            500
        );

        assertThat(occurrences).extracting(value -> value.toLocalDate()).containsExactly(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2032, 2, 29)
        );
    }

    @Test
    void keepsLocalTimeAcrossDaylightSavingTime() {
        RecurrenceRuleDTO rule = countRule(RecurrenceFrequency.WEEKLY, 1, 4);
        rule.setWeekDays(List.of(RecurrenceWeekDay.MO));

        var occurrences = generator.generate(LocalDateTime.of(2026, 10, 12, 20, 0), rome, rule, 500);

        assertThat(occurrences).allMatch(value -> value.toLocalTime().equals(LocalTime.of(20, 0)));
        assertThat(occurrences.get(0).getOffset()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(occurrences.get(3).getOffset()).isEqualTo(ZoneOffset.ofHours(1));
    }

    @Test
    void rejectsMoreThanTheConfiguredMaximum() {
        assertThatThrownBy(() -> generator.generate(
            LocalDateTime.of(2026, 9, 1, 20, 0),
            rome,
            countRule(RecurrenceFrequency.DAILY, 1, 501),
            500
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 500");
    }

    @Test
    void generatesUntilDateInclusively() {
        RecurrenceRuleDTO rule = new RecurrenceRuleDTO();
        rule.setFrequency(RecurrenceFrequency.DAILY);
        rule.setInterval(1);
        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(RecurrenceEndType.UNTIL);
        end.setUntil(LocalDate.of(2026, 9, 3));
        rule.setEnd(end);

        assertThat(generator.generate(LocalDateTime.of(2026, 9, 1, 20, 0), rome, rule, 500))
            .extracting(value -> value.toLocalDate())
            .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3));
    }

    private RecurrenceRuleDTO countRule(RecurrenceFrequency frequency, int interval, int count) {
        RecurrenceRuleDTO rule = new RecurrenceRuleDTO();
        rule.setFrequency(frequency);
        rule.setInterval(interval);
        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(RecurrenceEndType.COUNT);
        end.setCount(count);
        rule.setEnd(end);
        return rule;
    }
}
