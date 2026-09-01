package com.fundaro.zodiac.taurus.service.dto;

public class BulkAvailabilityResultDTO {
    private Long seriesId;
    private Integer affectedOccurrences;

    public BulkAvailabilityResultDTO() {}

    public BulkAvailabilityResultDTO(Long seriesId, Integer affectedOccurrences) {
        this.seriesId = seriesId;
        this.affectedOccurrences = affectedOccurrences;
    }

    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Integer getAffectedOccurrences() { return affectedOccurrences; }
    public void setAffectedOccurrences(Integer affectedOccurrences) { this.affectedOccurrences = affectedOccurrences; }
}
