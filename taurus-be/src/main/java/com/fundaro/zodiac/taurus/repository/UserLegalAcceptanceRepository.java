package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.UserLegalAcceptance;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLegalAcceptanceRepository extends JpaRepository<UserLegalAcceptance, Long> {

    List<UserLegalAcceptance> findAllByUserIdAndLegalDocument_IdInAndDeletedFalse(String userId, Collection<Long> legalDocumentIds);

    boolean existsByLegalDocument_Id(Long legalDocumentId);

    long deleteAllByUserId(String userId);

}
