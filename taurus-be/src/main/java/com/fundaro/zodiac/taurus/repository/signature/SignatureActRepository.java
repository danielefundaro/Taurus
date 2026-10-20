package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SignatureActRepository extends JpaRepository<SignatureAct, Long> {

    /**
     * Acts not yet purged in which the user signed or has to sign.
     */
    @Query("""
        select distinct a from SignatureAct a
        where a.deleted = false
          and a.purgedAt is null
          and exists (
            select s.id from SignatureActSigner s
            where s.actId = a.id and s.deleted = false and s.userKeycloakId = :userId
          )
        """)
    List<SignatureAct> findUnpurgedForSigner(@Param("userId") String userId);

    /**
     * Acts of the given types in which the user is a signer, most recent first.
     */
    @Query("""
        select distinct a from SignatureAct a
        where a.deleted = false
          and a.actType in :types
          and exists (
            select s.id from SignatureActSigner s
            where s.actId = a.id and s.deleted = false and s.userKeycloakId = :userId
          )
        order by a.id desc
        """)
    List<SignatureAct> findAllForSigner(@Param("userId") String userId, @Param("types") Collection<SignatureActType> types);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SignatureAct a where a.id = :id and a.deleted = false")
    Optional<SignatureAct> findForUpdate(@Param("id") Long id);

    Optional<SignatureAct> findByIdAndDeletedFalse(Long id);

    List<SignatureAct> findAllBySheetIdAndDeletedFalseOrderByIdAsc(Long sheetId);

    List<SignatureAct> findAllBySheetIdInAndDeletedFalse(Collection<Long> sheetIds);

    Optional<SignatureAct> findFirstBySheetIdAndActTypeAndDeletedFalseOrderByIdDesc(Long sheetId, SignatureActType actType);

    boolean existsBySheetIdAndStatusInAndDeletedFalse(Long sheetId, Collection<SignatureActStatus> statuses);

    /**
     * Signed acts whose grace period has elapsed and whose files and personal data are still in Taurus.
     */
    @Query("select a from SignatureAct a where a.deleted = false and a.purgedAt is null and a.purgeAfter is not null and a.purgeAfter < :now")
    List<SignatureAct> findDueForPurge(@Param("now") ZonedDateTime now);
}
