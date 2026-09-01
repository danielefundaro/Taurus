package com.fundaro.zodiac.taurus.service.dto;

public class CalendarEventSeriesRequest {
    private Long entityVersion;
    private Long sourceOccurrenceId;
    private CalendarEventsDTO template;
    private RecurrenceRuleDTO recurrence;

    public Long getEntityVersion() { return entityVersion; }
    public void setEntityVersion(Long entityVersion) { this.entityVersion = entityVersion; }
    public Long getSourceOccurrenceId() { return sourceOccurrenceId; }
    public void setSourceOccurrenceId(Long sourceOccurrenceId) { this.sourceOccurrenceId = sourceOccurrenceId; }
    public CalendarEventsDTO getTemplate() { return template; }
    public void setTemplate(CalendarEventsDTO template) { this.template = template; }
    public RecurrenceRuleDTO getRecurrence() { return recurrence; }
    public void setRecurrence(RecurrenceRuleDTO recurrence) { this.recurrence = recurrence; }
}
