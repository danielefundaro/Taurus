package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureChainHead;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SignatureChainHeadRepository extends JpaRepository<SignatureChainHead, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SignatureChainHead h where h.deleted = false")
    Optional<SignatureChainHead> findForUpdate();
}
