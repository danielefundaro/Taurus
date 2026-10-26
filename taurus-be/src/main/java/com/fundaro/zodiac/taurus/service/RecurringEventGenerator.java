package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceFrequency;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceWeekDay;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceEndDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceRuleDTO;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class RecurringEventGenerator {

    /**
     * Occorrenze generate dalla regola e mesi che la regola ha saltato perché il giorno scelto non
     * esiste in quel mese: chi crea la serie deve vederli prima di salvare.
     */
    public record Generation(List<ZonedDateTime> occurrences, List<YearMonth> skippedMonths) {
    }

    public List<ZonedDateTime> generate(LocalDateTime firstStart, ZoneId zoneId, RecurrenceRuleDTO rule, int maximumOccurrences) {
        return generateDetailed(firstStart, zoneId, rule, maximumOccurrences).occurrences();
    }

    public Generation generateDetailed(LocalDateTime firstStart, ZoneId zoneId, RecurrenceRuleDTO rule, int maximumOccurrences) {
        validate(firstStart, rule, maximumOccurrences);
        List<YearMonth> skipped = new ArrayList<>();
        List<ZonedDateTime> occurrences = switch (rule.getFrequency()) {
            case DAILY -> generateDaily(firstStart, zoneId, rule, maximumOccurrences);
            case WEEKLY -> generateWeekly(firstStart, zoneId, rule, maximumOccurrences);
            case MONTHLY -> generateMonthly(firstStart, zoneId, rule, maximumOccurrences, skipped);
            case YEARLY -> generateYearly(firstStart, zoneId, rule, maximumOccurrences, skipped);
        };
        return new Generation(occurrences, List.copyOf(skipped));
    }

    private List<ZonedDateTime> generateDaily(LocalDateTime first, ZoneId zone, RecurrenceRuleDTO rule, int maximum) {
        List<ZonedDateTime> result = new ArrayList<>();
        LocalDateTime candidate = first;
        while (shouldContinue(candidate.toLocalDate(), result.size(), rule.getEnd())) {
            add(result, candidate, zone, maximum);
            candidate = candidate.plusDays(rule.getInterval());
        }
        return result;
    }

    private List<ZonedDateTime> generateWeekly(LocalDateTime first, ZoneId zone, RecurrenceRuleDTO rule, int maximum) {
        Set<DayOfWeek> selected = EnumSet.noneOf(DayOfWeek.class);
        rule.getWeekDays().forEach(day -> selected.add(day.toDayOfWeek()));
        if (!selected.contains(first.getDayOfWeek())) {
            throw new IllegalArgumentException("The first occurrence day must be selected");
        }

        List<ZonedDateTime> result = new ArrayList<>();
        LocalDate firstDate = first.toLocalDate();
        LocalDate firstWeek = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate candidate = firstDate;
        while (shouldContinue(candidate, result.size(), rule.getEnd())) {
            LocalDate candidateWeek = candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            long weeks = ChronoUnit.WEEKS.between(firstWeek, candidateWeek);
            if (weeks % rule.getInterval() == 0 && selected.contains(candidate.getDayOfWeek())) {
                add(result, LocalDateTime.of(candidate, first.toLocalTime()), zone, maximum);
            }
            candidate = candidate.plusDays(1);
        }
        return result;
    }

    private List<ZonedDateTime> generateMonthly(LocalDateTime first, ZoneId zone, RecurrenceRuleDTO rule, int maximum, List<YearMonth> skipped) {
        List<ZonedDateTime> result = new ArrayList<>();
        YearMonth month = YearMonth.from(first);
        int day = first.getDayOfMonth();
        while (true) {
            LocalDate candidateDate = day <= month.lengthOfMonth() ? month.atDay(day) : null;
            if (candidateDate != null) {
                if (!shouldContinue(candidateDate, result.size(), rule.getEnd())) break;
                add(result, LocalDateTime.of(candidateDate, first.toLocalTime()), zone, maximum);
            } else if (pastUntil(month.atEndOfMonth(), rule.getEnd())) {
                break;
            } else {
                skipped.add(month);
            }
            if (reachedCount(result.size(), rule.getEnd())) break;
            month = month.plusMonths(rule.getInterval());
        }
        return result;
    }

    private List<ZonedDateTime> generateYearly(LocalDateTime first, ZoneId zone, RecurrenceRuleDTO rule, int maximum, List<YearMonth> skipped) {
        List<ZonedDateTime> result = new ArrayList<>();
        int year = first.getYear();
        int month = first.getMonthValue();
        int day = first.getDayOfMonth();
        while (true) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate candidateDate = day <= yearMonth.lengthOfMonth() ? yearMonth.atDay(day) : null;
            if (candidateDate != null) {
                if (!shouldContinue(candidateDate, result.size(), rule.getEnd())) break;
                add(result, LocalDateTime.of(candidateDate, first.toLocalTime()), zone, maximum);
            } else if (pastUntil(yearMonth.atEndOfMonth(), rule.getEnd())) {
                break;
            } else {
                skipped.add(yearMonth);
            }
            if (reachedCount(result.size(), rule.getEnd())) break;
            year += rule.getInterval();
        }
        return result;
    }

    private boolean shouldContinue(LocalDate candidate, int currentCount, RecurrenceEndDTO end) {
        if (end.getType() == RecurrenceEndType.COUNT) return currentCount < end.getCount();
        return !candidate.isAfter(end.getUntil());
    }

    private boolean reachedCount(int currentCount, RecurrenceEndDTO end) {
        return end.getType() == RecurrenceEndType.COUNT && currentCount >= end.getCount();
    }

    private boolean pastUntil(LocalDate candidate, RecurrenceEndDTO end) {
        return end.getType() == RecurrenceEndType.UNTIL && candidate.isAfter(end.getUntil());
    }

    private void add(List<ZonedDateTime> result, LocalDateTime candidate, ZoneId zone, int maximum) {
        if (result.size() >= maximum) {
            throw new OccurrenceLimitExceededException(maximum);
        }
        result.add(candidate.atZone(zone));
    }

    private void validate(LocalDateTime first, RecurrenceRuleDTO rule, int maximum) {
        if (first == null || rule == null || rule.getFrequency() == null || rule.getEnd() == null || rule.getEnd().getType() == null) {
            throw new IllegalArgumentException("Incomplete recurrence rule");
        }
        if (rule.getInterval() == null || rule.getInterval() < 1 || rule.getInterval() > 366) {
            throw new IllegalArgumentException("Recurrence interval must be between 1 and 366");
        }
        if (maximum < 1) throw new IllegalArgumentException("Invalid maximum occurrence limit");
        if (rule.getEnd().getType() == RecurrenceEndType.COUNT) {
            if (rule.getEnd().getCount() != null && rule.getEnd().getCount() > maximum) {
                throw new OccurrenceLimitExceededException(maximum);
            }
            if (rule.getEnd().getCount() == null || rule.getEnd().getCount() < 1) {
                throw new IllegalArgumentException("Occurrence count must be at least 1");
            }
            if (rule.getEnd().getUntil() != null)
                throw new IllegalArgumentException("COUNT cannot include an until date");
        } else {
            if (rule.getEnd().getUntil() == null || rule.getEnd().getUntil().isBefore(first.toLocalDate())) {
                throw new IllegalArgumentException("Invalid recurrence end date");
            }
            if (rule.getEnd().getCount() != null) throw new IllegalArgumentException("UNTIL cannot include a count");
        }
        List<RecurrenceWeekDay> weekDays = rule.getWeekDays();
        if (rule.getFrequency() == RecurrenceFrequency.WEEKLY && (weekDays == null || weekDays.isEmpty())) {
            throw new IllegalArgumentException("A weekly recurrence requires at least one weekday");
        }
    }
}
