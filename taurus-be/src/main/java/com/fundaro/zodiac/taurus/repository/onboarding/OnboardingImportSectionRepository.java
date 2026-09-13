package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnboardingImportSectionRepository extends JpaRepository<OnboardingImportSection, Long> {
    List<OnboardingImportSection> findAllByJob_IdOrderBySectionAsc(Long jobId);

    void deleteAllByJob_Id(Long jobId);
}
