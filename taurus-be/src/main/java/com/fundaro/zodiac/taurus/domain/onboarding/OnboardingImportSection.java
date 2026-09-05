package com.fundaro.zodiac.taurus.domain.onboarding;

import jakarta.persistence.*;

@Entity @Table(name = "onboarding_import_section")
public class OnboardingImportSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private OnboardingImportJob job;
    @Enumerated(EnumType.STRING) @Column(name = "section", nullable = false, length = 32) private OnboardingSection section;
    @Column(name = "total_count", nullable = false) private int total;
    @Column(name = "valid_count", nullable = false) private int valid;
    @Column(name = "warning_count", nullable = false) private int warning;
    @Column(name = "error_count", nullable = false) private int error;
    @Column(name = "create_count", nullable = false) private int createCount;
    @Column(name = "reuse_count", nullable = false) private int reuseCount;
    @Column(name = "skip_count", nullable = false) private int skipCount;
    @Column(name = "applied_count", nullable = false) private int applied;
    public Long getId() { return id; }
    public OnboardingImportJob getJob() { return job; } public void setJob(OnboardingImportJob v) { job = v; }
    public OnboardingSection getSection() { return section; } public void setSection(OnboardingSection v) { section = v; }
    public int getTotal() { return total; } public void setTotal(int v) { total = v; }
    public int getValid() { return valid; } public void setValid(int v) { valid = v; }
    public int getWarning() { return warning; } public void setWarning(int v) { warning = v; }
    public int getError() { return error; } public void setError(int v) { error = v; }
    public int getCreateCount() { return createCount; } public void setCreateCount(int v) { createCount = v; }
    public int getReuseCount() { return reuseCount; } public void setReuseCount(int v) { reuseCount = v; }
    public int getSkipCount() { return skipCount; } public void setSkipCount(int v) { skipCount = v; }
    public int getApplied() { return applied; } public void setApplied(int v) { applied = v; }
}
