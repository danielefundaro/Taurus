package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.UserLegalAcceptance;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserLegalAcceptanceRepository extends JpaRepository<UserLegalAcceptance, Long> {

    List<UserLegalAcceptance> findAllByUserIdAndLegalDocument_IdInAndDeletedFalse(String userId, Collection<Long> legalDocumentIds);

    boolean existsByLegalDocument_Id(Long legalDocumentId);

    /** Whether the user confirmed, in the tenant, another version of the same document. */
    boolean existsByUserIdAndDeletedFalseAndLegalDocument_DocumentTypeAndLegalDocument_IdNot(
        String userId,
        LegalDocumentType documentType,
        Long legalDocumentId
    );

    long deleteAllByUserId(String userId);

}
