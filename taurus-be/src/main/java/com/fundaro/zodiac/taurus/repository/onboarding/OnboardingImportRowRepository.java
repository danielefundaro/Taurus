package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportRow;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingRowStatus;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingSection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OnboardingImportRowRepository extends JpaRepository<OnboardingImportRow, Long> {
    Page<OnboardingImportRow> findAllByJob_Id(Long jobId, Pageable pageable);

    Page<OnboardingImportRow> findAllByJob_IdAndSection(Long jobId, OnboardingSection section, Pageable pageable);

    Page<OnboardingImportRow> findAllByJob_IdAndStatus(Long jobId, OnboardingRowStatus status, Pageable pageable);

    Page<OnboardingImportRow> findAllByJob_IdAndSectionAndStatus(Long jobId, OnboardingSection section, OnboardingRowStatus status, Pageable pageable);

    List<OnboardingImportRow> findAllByJob_IdOrderBySectionAscRowNumberAsc(Long jobId);

    /**
     * Svuota lo staging del job con una sola istruzione. La cancellazione derivata caricherebbe le righe e le
     * rimuoverebbe a una a una: Hibernate scriverebbe gli inserimenti della nuova validazione prima di quelle
     * rimozioni, violando il vincolo di unicità, oltre a tenere in memoria tutte le righe del file.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from OnboardingImportRow entity where entity.job.id = :jobId")
    void deleteAllByJob_Id(@Param("jobId") Long jobId);
}
