package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the Notices entity.
 */
@SuppressWarnings("unused")
@Repository
public interface NoticesRepository extends CommonRepository<Notices, NoticesCriteria> {

    @Query("SELECT n FROM Notices n WHERE n.userId = :userId AND n.readDate IS NULL AND n.deleted = false")
    List<Notices> findAllUnread(@Param("userId") String userId);

    @Query("SELECT COUNT(n) FROM Notices n WHERE n.userId = :userId AND n.readDate IS NULL AND n.deleted = false")
    long countUnread(@Param("userId") String userId);
}
