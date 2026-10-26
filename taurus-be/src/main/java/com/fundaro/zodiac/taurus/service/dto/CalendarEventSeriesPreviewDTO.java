package com.fundaro.zodiac.taurus.service.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CalendarEventSeriesPreviewDTO {
    private String timeZone;
    private Integer occurrenceCount;
    private List<Date> occurrences = new ArrayList<>();
    private Date lastOccurrence;
    /** Mesi che la regola salta perché il giorno scelto non esiste, in forma ISO `aaaa-mm`. */
    private List<String> skippedMonths = new ArrayList<>();

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Integer getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(Integer occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public List<Date> getOccurrences() {
        return occurrences;
    }

    public void setOccurrences(List<Date> occurrences) {
        this.occurrences = occurrences;
    }

    public List<String> getSkippedMonths() {
        return skippedMonths;
    }

    public void setSkippedMonths(List<String> skippedMonths) {
        this.skippedMonths = skippedMonths;
    }

    public Date getLastOccurrence() {
        return lastOccurrence;
    }

    public void setLastOccurrence(Date lastOccurrence) {
        this.lastOccurrence = lastOccurrence;
    }
}
