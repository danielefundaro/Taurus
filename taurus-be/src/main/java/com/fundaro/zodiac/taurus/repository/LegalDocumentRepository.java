package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.LegalDocument;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    List<LegalDocument> findAllByOrderByDocumentTypeAscPublishedAtDesc();

    List<LegalDocument> findAllByActiveTrueOrderByDocumentTypeAsc();

    boolean existsByDocumentTypeAndVersion(LegalDocumentType documentType, String version);

    boolean existsByDocumentTypeAndVersionAndIdNot(LegalDocumentType documentType, String version, Long id);

    @Modifying
    @Query("update LegalDocument d set d.active = false where d.documentType = :documentType and (:excludedId is null or d.id <> :excludedId)")
    void deactivateOtherVersions(@Param("documentType") LegalDocumentType documentType, @Param("excludedId") Long excludedId);
}
