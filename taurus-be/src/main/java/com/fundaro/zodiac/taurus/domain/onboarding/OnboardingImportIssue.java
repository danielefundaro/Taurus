package com.fundaro.zodiac.taurus.domain.onboarding;

import jakarta.persistence.*;

@Entity @Table(name = "onboarding_import_issue")
public class OnboardingImportIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private OnboardingImportJob job;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "row_id") private OnboardingImportRow row;
    @Enumerated(EnumType.STRING) @Column(name = "severity", nullable = false, length = 8) private OnboardingIssueSeverity severity;
    @Column(name = "code", nullable = false, length = 128) private String code;
    @Enumerated(EnumType.STRING) @Column(name = "section", length = 32) private OnboardingSection section;
    @Column(name = "row_number") private Integer rowNumber;
    @Column(name = "column_name", length = 128) private String columnName;
    @Column(name = "message", nullable = false, columnDefinition = "text") private String message;
    @Column(name = "suggestion", columnDefinition = "text") private String suggestion;
    public Long getId() { return id; }
    public OnboardingImportJob getJob() { return job; } public void setJob(OnboardingImportJob v) { job = v; }
    public OnboardingImportRow getRow() { return row; } public void setRow(OnboardingImportRow v) { row = v; }
    public OnboardingIssueSeverity getSeverity() { return severity; } public void setSeverity(OnboardingIssueSeverity v) { severity = v; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public OnboardingSection getSection() { return section; } public void setSection(OnboardingSection v) { section = v; }
    public Integer getRowNumber() { return rowNumber; } public void setRowNumber(Integer v) { rowNumber = v; }
    public String getColumnName() { return columnName; } public void setColumnName(String v) { columnName = v; }
    public String getMessage() { return message; } public void setMessage(String v) { message = v; }
    public String getSuggestion() { return suggestion; } public void setSuggestion(String v) { suggestion = v; }
}
