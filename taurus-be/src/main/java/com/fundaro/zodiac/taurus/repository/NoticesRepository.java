package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * Spring Data JPA repository for the Notices entity.
 */
@SuppressWarnings("unused")
@Repository
public interface NoticesRepository extends CommonRepository<Notices, NoticesCriteria> {

    @Query("SELECT n FROM Notices n WHERE n.userId = :userId AND n.readDate IS NULL AND n.deleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= CURRENT_TIMESTAMP)")
    List<Notices> findAllUnread(@Param("userId") String userId);

    @Query("SELECT COUNT(n) FROM Notices n WHERE n.userId = :userId AND n.readDate IS NULL AND n.deleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= CURRENT_TIMESTAMP)")
    long countUnread(@Param("userId") String userId);

    @Query("SELECT COUNT(n) FROM Notices n WHERE n.userId = :userId AND n.readDate IS NULL AND n.deleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= CURRENT_TIMESTAMP) AND n.source NOT IN :excludedSources")
    long countUnreadExcludingSources(@Param("userId") String userId, @Param("excludedSources") Collection<String> excludedSources);

    Optional<Notices> findBySourceEventKeyAndUserId(String sourceEventKey, String userId);
}
