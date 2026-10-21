package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidence;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SignatureEvidenceRepository extends JpaRepository<SignatureEvidence, Long> {

    List<SignatureEvidence> findAllByActIdOrderBySequenceAsc(Long actId);

    List<SignatureEvidence> findAllBySignerKeycloakIdAndPurgedAtIsNull(String signerKeycloakId);

    List<SignatureEvidence> findAllByActIdInOrderBySequenceAsc(java.util.Collection<Long> actIds);

    List<SignatureEvidence> findAllByActIdAndPurgedAtIsNull(Long actId);

    boolean existsByActId(Long actId);

    /**
     * A page of the chain, in order, starting after the given sequence.
     */
    @Query("select e from SignatureEvidence e where e.sequence > :after order by e.sequence asc")
    List<SignatureEvidence> findChainPage(@Param("after") long after, Pageable pageable);
}
