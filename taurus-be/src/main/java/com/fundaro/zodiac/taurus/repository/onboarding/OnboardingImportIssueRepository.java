package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingImportIssueRepository extends JpaRepository<OnboardingImportIssue, Long> {
    Page<OnboardingImportIssue> findAllByJob_Id(Long jobId, Pageable pageable);
    Page<OnboardingImportIssue> findAllByJob_IdAndSeverity(Long jobId, OnboardingIssueSeverity severity, Pageable pageable);
    Page<OnboardingImportIssue> findAllByJob_IdAndSection(Long jobId, OnboardingSection section, Pageable pageable);
    Page<OnboardingImportIssue> findAllByJob_IdAndSeverityAndSection(Long jobId, OnboardingIssueSeverity severity, OnboardingSection section, Pageable pageable);
    void deleteAllByJob_Id(Long jobId);
}
