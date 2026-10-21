package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLink;
import com.fundaro.zodiac.taurus.domain.signature.SignerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SignatureLinkRepository extends JpaRepository<SignatureLink, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SignatureLink l where l.id = :id and l.deleted = false")
    Optional<SignatureLink> findForUpdate(@Param("id") Long id);

    List<SignatureLink> findAllBySignerIdInAndDeletedFalseOrderByIdAsc(Collection<Long> signerIds);

    /**
     * Links of the signer not revoked and not expired yet: at most one, because a new link revokes the previous one.
     */
    @Query("""
        select l from SignatureLink l
        where l.signerId = :signerId and l.deleted = false and l.revokedAt is null and l.expiresAt > :now
        order by l.id desc
        """)
    List<SignatureLink> findActiveBySigner(@Param("signerId") Long signerId, @Param("now") ZonedDateTime now);

    /**
     * Links expired without the signature of their signer, while the act still waits, not yet notified to the
     * administrators.
     */
    @Query("""
        select l from SignatureLink l, SignatureActSigner s, SignatureAct a
        where s.id = l.signerId and a.id = s.actId
          and l.deleted = false and l.revokedAt is null and l.expiryNotifiedAt is null and l.expiresAt < :now
          and s.deleted = false and s.status = :pending
          and a.deleted = false and a.status in :awaiting
        order by l.id asc
        """)
    List<SignatureLink> findExpiredUnsigned(
        @Param("now") ZonedDateTime now,
        @Param("pending") SignerStatus pending,
        @Param("awaiting") Collection<SignatureActStatus> awaiting
    );
}
