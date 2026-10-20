package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureChallenge;
import com.fundaro.zodiac.taurus.domain.signature.SignatureChallengeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SignatureChallengeRepository extends JpaRepository<SignatureChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SignatureChallenge c where c.id = :id and c.deleted = false")
    Optional<SignatureChallenge> findForUpdate(@Param("id") Long id);

    /**
     * Removes the challenges closed, or expired, before the given instant: they are no longer needed by the evidence.
     */
    @Modifying
    @Query("""
        delete from SignatureChallenge c
        where (c.status <> :open and coalesce(c.confirmedAt, c.editDate) < :before)
           or c.expiresAt < :before
        """)
    int deleteClosedBefore(@Param("before") ZonedDateTime before, @Param("open") SignatureChallengeStatus open);

    @Modifying
    @Query("delete from SignatureChallenge c where c.userKeycloakId = :userId")
    int deleteAllByUser(@Param("userId") String userId);

    /**
     * Removes the challenges of the links of a parent: they hold the destination address.
     */
    @Modifying
    @Query("delete from SignatureChallenge c where c.linkId in :linkIds")
    int deleteAllByLinkIds(@Param("linkIds") Collection<Long> linkIds);

    /**
     * Creation instants of the challenges of the user after the given one, oldest first.
     */
    @Query("select c.insertDate from SignatureChallenge c where c.userKeycloakId = :userId and c.insertDate > :since order by c.insertDate asc")
    List<ZonedDateTime> findRecentCreations(@Param("userId") String userId, @Param("since") ZonedDateTime since);

    /**
     * Creation instants of the challenges of a link after the given one, oldest first.
     */
    @Query("select c.insertDate from SignatureChallenge c where c.linkId = :linkId and c.insertDate > :since order by c.insertDate asc")
    List<ZonedDateTime> findRecentLinkCreations(@Param("linkId") Long linkId, @Param("since") ZonedDateTime since);
}
