package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OnboardingImportSectionRepository extends JpaRepository<OnboardingImportSection, Long> {
    List<OnboardingImportSection> findAllByJob_IdOrderBySectionAsc(Long jobId);

    /**
     * Svuota lo staging del job con una sola istruzione. La cancellazione derivata caricherebbe le righe e le
     * rimuoverebbe a una a una: Hibernate scriverebbe gli inserimenti della nuova validazione prima di quelle
     * rimozioni, violando il vincolo di unicità, oltre a tenere in memoria tutte le righe del file.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from OnboardingImportSection entity where entity.job.id = :jobId")
    void deleteAllByJob_Id(@Param("jobId") Long jobId);
}
