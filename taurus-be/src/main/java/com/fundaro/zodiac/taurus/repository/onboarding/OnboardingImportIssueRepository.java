package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportIssue;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingIssueSeverity;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingSection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnboardingImportIssueRepository extends JpaRepository<OnboardingImportIssue, Long> {
    Page<OnboardingImportIssue> findAllByJob_Id(Long jobId, Pageable pageable);

    Page<OnboardingImportIssue> findAllByJob_IdAndSeverity(Long jobId, OnboardingIssueSeverity severity, Pageable pageable);

    Page<OnboardingImportIssue> findAllByJob_IdAndSection(Long jobId, OnboardingSection section, Pageable pageable);

    Page<OnboardingImportIssue> findAllByJob_IdAndSeverityAndSection(Long jobId, OnboardingIssueSeverity severity, OnboardingSection section, Pageable pageable);

    /**
     * Svuota lo staging del job con una sola istruzione. La cancellazione derivata caricherebbe le righe e le
     * rimuoverebbe a una a una: Hibernate scriverebbe gli inserimenti della nuova validazione prima di quelle
     * rimozioni, violando il vincolo di unicità, oltre a tenere in memoria tutte le righe del file.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from OnboardingImportIssue entity where entity.job.id = :jobId")
    void deleteAllByJob_Id(@Param("jobId") Long jobId);
}
