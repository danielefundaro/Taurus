package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingImportRowRepository extends JpaRepository<OnboardingImportRow, Long> {
    Page<OnboardingImportRow> findAllByJob_Id(Long jobId, Pageable pageable);
    Page<OnboardingImportRow> findAllByJob_IdAndSection(Long jobId, OnboardingSection section, Pageable pageable);
    Page<OnboardingImportRow> findAllByJob_IdAndStatus(Long jobId, OnboardingRowStatus status, Pageable pageable);
    Page<OnboardingImportRow> findAllByJob_IdAndSectionAndStatus(Long jobId, OnboardingSection section, OnboardingRowStatus status, Pageable pageable);
    List<OnboardingImportRow> findAllByJob_IdOrderBySectionAscRowNumberAsc(Long jobId);
    void deleteAllByJob_Id(Long jobId);
}
