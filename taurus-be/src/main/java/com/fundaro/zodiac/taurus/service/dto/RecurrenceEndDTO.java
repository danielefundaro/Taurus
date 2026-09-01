package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import java.time.LocalDate;

public class RecurrenceEndDTO {
    private RecurrenceEndType type;
    private Integer count;
    private LocalDate until;

    public RecurrenceEndType getType() { return type; }
    public void setType(RecurrenceEndType type) { this.type = type; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public LocalDate getUntil() { return until; }
    public void setUntil(LocalDate until) { this.until = until; }
}
