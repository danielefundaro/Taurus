package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceFrequency;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceWeekDay;
import java.util.ArrayList;
import java.util.List;

public class RecurrenceRuleDTO {
    private RecurrenceFrequency frequency;
    private Integer interval = 1;
    private List<RecurrenceWeekDay> weekDays = new ArrayList<>();
    private RecurrenceEndDTO end;

    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }
    public Integer getInterval() { return interval; }
    public void setInterval(Integer interval) { this.interval = interval; }
    public List<RecurrenceWeekDay> getWeekDays() { return weekDays; }
    public void setWeekDays(List<RecurrenceWeekDay> weekDays) { this.weekDays = weekDays; }
    public RecurrenceEndDTO getEnd() { return end; }
    public void setEnd(RecurrenceEndDTO end) { this.end = end; }
}
