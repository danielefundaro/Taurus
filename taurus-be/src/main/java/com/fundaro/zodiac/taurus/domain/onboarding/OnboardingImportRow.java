package com.fundaro.zodiac.taurus.domain.onboarding;

import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "onboarding_import_row")
public class OnboardingImportRow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private OnboardingImportJob job;
    @Enumerated(EnumType.STRING) @Column(name = "section", nullable = false, length = 32) private OnboardingSection section;
    @Column(name = "row_number", nullable = false) private int rowNumber;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16) private OnboardingRowStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "action", nullable = false, length = 16) private OnboardingRowAction action = OnboardingRowAction.CREATE;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "normalized_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> normalizedPayload = new LinkedHashMap<>();
    public Long getId() { return id; }
    public OnboardingImportJob getJob() { return job; } public void setJob(OnboardingImportJob v) { job = v; }
    public OnboardingSection getSection() { return section; } public void setSection(OnboardingSection v) { section = v; }
    public int getRowNumber() { return rowNumber; } public void setRowNumber(int v) { rowNumber = v; }
    public OnboardingRowStatus getStatus() { return status; } public void setStatus(OnboardingRowStatus v) { status = v; }
    public OnboardingRowAction getAction() { return action; } public void setAction(OnboardingRowAction v) { action = v; }
    public Map<String, Object> getNormalizedPayload() { return normalizedPayload; } public void setNormalizedPayload(Map<String, Object> v) { normalizedPayload = v; }
}
