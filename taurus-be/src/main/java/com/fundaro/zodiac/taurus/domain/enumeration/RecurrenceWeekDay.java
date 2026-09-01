package com.fundaro.zodiac.taurus.domain.enumeration;

import java.time.DayOfWeek;

public enum RecurrenceWeekDay {
    MO(DayOfWeek.MONDAY),
    TU(DayOfWeek.TUESDAY),
    WE(DayOfWeek.WEDNESDAY),
    TH(DayOfWeek.THURSDAY),
    FR(DayOfWeek.FRIDAY),
    SA(DayOfWeek.SATURDAY),
    SU(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    RecurrenceWeekDay(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }

    public static RecurrenceWeekDay from(DayOfWeek dayOfWeek) {
        for (RecurrenceWeekDay value : values()) {
            if (value.dayOfWeek == dayOfWeek) return value;
        }
        throw new IllegalArgumentException("Unsupported day of week: " + dayOfWeek);
    }
}
