package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SignatureActSignerRepository extends JpaRepository<SignatureActSigner, Long> {
    List<SignatureActSigner> findAllByActIdAndDeletedFalseOrderByIdAsc(Long actId);

    List<SignatureActSigner> findAllByActIdInAndDeletedFalseOrderByIdAsc(Collection<Long> actIds);

    List<SignatureActSigner> findAllByUserKeycloakIdAndDeletedFalse(String userKeycloakId);
}
