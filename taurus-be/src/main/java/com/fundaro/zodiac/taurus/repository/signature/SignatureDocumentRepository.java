package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureDocument;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SignatureDocumentRepository extends JpaRepository<SignatureDocument, Long> {
    List<SignatureDocument> findAllByActIdAndDeletedFalseOrderByIdAsc(Long actId);

    List<SignatureDocument> findAllByActIdInAndDeletedFalseOrderByIdAsc(Collection<Long> actIds);

    Optional<SignatureDocument> findFirstByActIdAndRoleAndDeletedFalse(Long actId, SignatureDocumentRole role);
}
