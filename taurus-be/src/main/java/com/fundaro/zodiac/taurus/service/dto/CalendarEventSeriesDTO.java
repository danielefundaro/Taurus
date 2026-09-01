package com.fundaro.zodiac.taurus.service.dto;

public class CalendarEventSeriesDTO {
    private Long id;
    private Long entityVersion;
    private String timeZone;
    private CalendarEventsDTO template;
    private RecurrenceRuleDTO recurrence;
    private Integer occurrenceCount;
    private Integer exceptionCount;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer deletedCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEntityVersion() { return entityVersion; }
    public void setEntityVersion(Long entityVersion) { this.entityVersion = entityVersion; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public CalendarEventsDTO getTemplate() { return template; }
    public void setTemplate(CalendarEventsDTO template) { this.template = template; }
    public RecurrenceRuleDTO getRecurrence() { return recurrence; }
    public void setRecurrence(RecurrenceRuleDTO recurrence) { this.recurrence = recurrence; }
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public Integer getExceptionCount() { return exceptionCount; }
    public void setExceptionCount(Integer exceptionCount) { this.exceptionCount = exceptionCount; }
    public Integer getCreatedCount() { return createdCount; }
    public void setCreatedCount(Integer createdCount) { this.createdCount = createdCount; }
    public Integer getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(Integer updatedCount) { this.updatedCount = updatedCount; }
    public Integer getDeletedCount() { return deletedCount; }
    public void setDeletedCount(Integer deletedCount) { this.deletedCount = deletedCount; }
}
